package com.example.fraud.llm;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.FraudAlert;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.rule.*;
import com.example.fraud.schema.SchemaIndexService;
import com.example.fraud.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class LlmRuleScheduler {

    private final RuleRepository ruleRepository;
    private final LlmClient llmClient;
    private final LogstashEventPublisher publisher;
    private final ElasticsearchTemplate elasticsearchTemplate;

    private final Map<Long, Instant> lastRunTimes = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<RuleEntity> rules = ruleRepository.findByRuleTypeAndStatus(
            RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE);

        for (RuleEntity rule : rules) {
            if (!isDue(rule)) {
                continue;
            }

            try {
                TenantContext.setTenantId(rule.getTenantId());
                evaluateRule(rule);
                lastRunTimes.put(rule.getId(), Instant.now());
            } catch (Exception e) {
                log.error("LLM evaluation failed for rule id={} name='{}'", rule.getId(), rule.getName(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private boolean isDue(RuleEntity rule) {
        Instant lastRun = lastRunTimes.get(rule.getId());
        if (lastRun == null) {
            return true;
        }
        long minutesSinceLastRun = ChronoUnit.MINUTES.between(lastRun, Instant.now());
        return minutesSinceLastRun >= rule.getEvaluationIntervalMinutes();
    }

    private void evaluateRule(RuleEntity rule) {
        List<EventDocument> events = fetchRecentEvents(rule);
        if (events.isEmpty()) {
            log.debug("No events found for LLM rule id={} name='{}'", rule.getId(), rule.getName());
            return;
        }

        String eventsJson = serializeEvents(events);
        LlmVerdict verdict = llmClient.evaluate(rule.getPromptTemplate(), eventsJson);

        log.info("LLM rule '{}' evaluated: eventCount={} matched={} reasoning={}",
            rule.getName(), events.size(), verdict.matched(),
            verdict.reasoning().length() > 200 ? verdict.reasoning().substring(0, 200) + "..." : verdict.reasoning());

        if (verdict.matched()) {
            for (EventDocument event : events) {
                FraudAlert alert = new FraudAlert(
                    UUID.randomUUID().toString(),
                    rule.getTenantId(),
                    event.id(),
                    rule.getName(),
                    "HIGH",
                    "Rule '" + rule.getName() + "': " + verdict.reasoning(),
                    Instant.now()
                );
                publisher.writeAlert(alert);
            }
        }
    }

    private List<EventDocument> fetchRecentEvents(RuleEntity rule) {
        Instant cutoff = Instant.now().minus(rule.getTimeWindowMinutes(), ChronoUnit.MINUTES);
        String indexName = SchemaIndexService.tenantIndexName(rule.getTenantId());

        var query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> b
                .must(QueryBuilders.term(t -> t.field("eventType").value(rule.getEventType())))
                .must(QueryBuilders.range(r -> r.date(d -> d.field("eventTime").gte(cutoff.toString()))))))
            .withMaxResults(1000)
            .build();

        return elasticsearchTemplate.search(query, EventDocument.class, IndexCoordinates.of(indexName))
            .getSearchHits().stream()
            .map(SearchHit::getContent)
            .toList();
    }

    private String serializeEvents(List<EventDocument> events) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        try {
            return mapper.writeValueAsString(events);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize events", e);
            return "[]";
        }
    }
}
