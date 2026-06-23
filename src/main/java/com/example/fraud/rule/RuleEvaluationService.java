package com.example.fraud.rule;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.example.fraud.event.EventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEvaluationService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    public List<RuleEntity> evaluate(EventDocument event, List<RuleEntity> rules) {
        return rules.stream()
            .filter(rule -> matches(event, rule))
            .toList();
    }

    private boolean matches(EventDocument event, RuleEntity rule) {
        return switch (rule.getRuleType()) {
            case CONDITION -> matchesConditionRule(event, rule);
            case VELOCITY -> matchesVelocityRule(event, rule);
        };
    }

    private boolean matchesConditionRule(EventDocument event, RuleEntity rule) {
        return rule.getParsedConditions().stream()
            .allMatch(c -> matchesCondition(event, c));
    }

    private boolean matchesVelocityRule(EventDocument event, RuleEntity rule) {
        Object groupValue = resolveField(event, rule.getGroupByField());
        if (groupValue == null) {
            return false;
        }

        Instant cutoff = Instant.now().minus(rule.getTimeWindowMinutes(), ChronoUnit.MINUTES);

        var query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> b
                .must(QueryBuilders.term(t -> t
                    .field("tenantId")
                    .value(event.tenantId())))
                .must(QueryBuilders.term(t -> t
                    .field(rule.getGroupByField())
                    .value(String.valueOf(groupValue))))
                .must(QueryBuilders.range(r -> r
                    .date(d -> d
                        .field("eventTime")
                        .gte(cutoff.toString()))))))
            .build();

        long count = elasticsearchTemplate.count(query, EventDocument.class,
            IndexCoordinates.of("events"));

        boolean exceeded = count >= rule.getThreshold();
        if (exceeded) {
            log.info("Velocity rule '{}' fired: {}={} count={} threshold={}",
                rule.getName(), rule.getGroupByField(), groupValue,
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
        if (field.startsWith("attributes.")) {
            String attrKey = field.substring("attributes.".length());
            Map<String, Object> attrs = event.attributes();
            return attrs != null ? attrs.get(attrKey) : null;
        }

        return switch (field) {
            case "riskScore" -> event.riskScore();
            case "eventType" -> event.eventType();
            case "customerId" -> event.customerId();
            case "sourceIp" -> event.sourceIp();
            case "deviceId" -> event.deviceId();
            case "email" -> event.email();
            case "phoneNumber" -> event.phoneNumber();
            default -> null;
        };
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
