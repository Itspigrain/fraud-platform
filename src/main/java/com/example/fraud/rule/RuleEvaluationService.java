package com.example.fraud.rule;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.example.fraud.event.EventDocument;
import com.example.fraud.schema.SchemaIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEvaluationService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    public List<RuleEntity> evaluate(EventDocument event, List<RuleEntity> rules) {
        List<RuleEntity> sorted = topologicalSort(rules);
        Set<Long> matchedIds = new HashSet<>();
        List<RuleEntity> matched = new ArrayList<>();

        for (RuleEntity rule : sorted) {
            if (!dependenciesSatisfied(rule, matchedIds)) continue;
            if (matches(event, rule)) {
                matchedIds.add(rule.getId());
                matched.add(rule);
            }
        }

        return matched;
    }

    private boolean dependenciesSatisfied(RuleEntity rule, Set<Long> matchedIds) {
        List<Long> deps = rule.getParsedDependsOn();
        if (deps.isEmpty()) return true;

        DependencyCondition condition = rule.getDependencyCondition();
        if (condition == null) condition = DependencyCondition.ALL;

        return switch (condition) {
            case ALL -> matchedIds.containsAll(deps);
            case ANY -> deps.stream().anyMatch(matchedIds::contains);
        };
    }

    List<RuleEntity> topologicalSort(List<RuleEntity> rules) {
        Map<Long, RuleEntity> ruleMap = rules.stream()
            .collect(Collectors.toMap(RuleEntity::getId, r -> r));

        Map<Long, Integer> inDegree = new HashMap<>();
        for (RuleEntity rule : rules) {
            inDegree.putIfAbsent(rule.getId(), 0);
            for (Long depId : rule.getParsedDependsOn()) {
                if (ruleMap.containsKey(depId)) {
                    inDegree.merge(rule.getId(), 1, Integer::sum);
                }
            }
        }

        Queue<Long> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<RuleEntity> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            sorted.add(ruleMap.get(id));
            for (RuleEntity rule : rules) {
                if (rule.getParsedDependsOn().contains(id) && ruleMap.containsKey(rule.getId())) {
                    int newDegree = inDegree.merge(rule.getId(), -1, Integer::sum);
                    if (newDegree == 0) queue.add(rule.getId());
                }
            }
        }

        // Any rules not in sorted (due to cycles among active rules) are appended at the end
        if (sorted.size() < rules.size()) {
            Set<Long> sortedIds = sorted.stream().map(RuleEntity::getId).collect(Collectors.toSet());
            for (RuleEntity rule : rules) {
                if (!sortedIds.contains(rule.getId())) sorted.add(rule);
            }
        }

        return sorted;
    }

    private boolean matches(EventDocument event, RuleEntity rule) {
        return switch (rule.getRuleType()) {
            case CONDITION -> matchesConditionRule(event, rule);
            case VELOCITY -> matchesVelocityRule(event, rule);
            case LLM_EVALUATOR -> false;
        };
    }

    private boolean matchesConditionRule(EventDocument event, RuleEntity rule) {
        return rule.getParsedConditions().stream()
            .allMatch(c -> matchesCondition(event, c));
    }

    private boolean matchesVelocityRule(EventDocument event, RuleEntity rule) {
        String groupByField = rule.getGroupByField();
        Object groupValue = resolveField(event, groupByField);
        if (groupValue == null) {
            return false;
        }

        String esField = groupByField.startsWith("attributes.") ? groupByField : "attributes." + groupByField;

        Instant cutoff = Instant.now().minus(rule.getTimeWindowMinutes(), ChronoUnit.MINUTES);
        String indexName = SchemaIndexService.tenantIndexName(event.tenantId());

        var query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> b
                .must(QueryBuilders.term(t -> t
                    .field(esField)
                    .value(String.valueOf(groupValue))))
                .must(QueryBuilders.range(r -> r
                    .date(d -> d
                        .field("eventTime")
                        .gte(cutoff.toString()))))))
            .build();

        long count = elasticsearchTemplate.count(query, EventDocument.class,
            IndexCoordinates.of(indexName));

        boolean exceeded = count >= rule.getThreshold();
        if (exceeded) {
            log.info("Velocity rule '{}' fired: {}={} count={} threshold={}",
                rule.getName(), groupByField, groupValue,
                count, rule.getThreshold());
        }
        return exceeded;
    }

    private boolean matchesCondition(EventDocument event, RuleCondition condition) {
        Object fieldValue = resolveField(event, condition.field());
        if (fieldValue == null) {
            return false;
        }

        String actual = String.valueOf(fieldValue);
        String expected = condition.value();

        return switch (condition.operator()) {
            case EQUALS -> actual.equals(expected);
            case NOT_EQUALS -> !actual.equals(expected);
            case GREATER_THAN -> compareNumeric(actual, expected) > 0;
            case LESS_THAN -> compareNumeric(actual, expected) < 0;
            case GREATER_THAN_OR_EQUAL -> compareNumeric(actual, expected) >= 0;
            case LESS_THAN_OR_EQUAL -> compareNumeric(actual, expected) <= 0;
            case CONTAINS -> actual.toLowerCase().contains(expected.toLowerCase());
            case IN -> Arrays.asList(expected.split(",")).contains(actual);
        };
    }

    Object resolveField(EventDocument event, String field) {
        if (field.equals("eventType")) return event.eventType();

        String attrKey = field.startsWith("attributes.") ? field.substring("attributes.".length()) : field;
        Map<String, Object> attrs = event.attributes();
        return attrs != null ? attrs.get(attrKey) : null;
    }

    private int compareNumeric(String actual, String expected) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(expected));
        } catch (NumberFormatException e) {
            log.warn("Non-numeric comparison attempted: actual={}, expected={}", actual, expected);
            return 0;
        }
    }
}
