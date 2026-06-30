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
import java.util.LinkedHashMap;
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

    public EvaluationResult evaluate(EventDocument event, List<RuleEntity> rules) {
        List<RuleEntity> sorted = topologicalSort(rules);
        Set<Long> matchedIds = new HashSet<>();
        List<RuleEntity> matched = new ArrayList<>();
        Map<String, Object> features = new LinkedHashMap<>();

        for (RuleEntity rule : sorted) {
            if (!dependenciesSatisfied(rule, matchedIds)) continue;

            boolean isMatch = switch (rule.getRuleType()) {
                case CONDITION -> evaluateConditionRule(event, rule, features);
                case VELOCITY -> evaluateVelocityRule(event, rule, features);
                case LLM_EVALUATOR -> false;
            };

            if (isMatch) {
                matchedIds.add(rule.getId());
                matched.add(rule);
            }
        }

        return new EvaluationResult(matched, features);
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

    private boolean evaluateConditionRule(EventDocument event, RuleEntity rule,
                                           Map<String, Object> features) {
        String prefix = sanitizeRuleName(rule.getName());
        boolean allMatch = true;

        for (RuleCondition condition : rule.getParsedConditions()) {
            Object fieldValue = resolveField(event, condition.field(), features);
            String fieldName = condition.field();
            if (fieldName.startsWith("attributes.")) {
                fieldName = fieldName.substring("attributes.".length());
            } else if (fieldName.startsWith("exports.")) {
                fieldName = fieldName.substring("exports.".length());
            }
            features.put(prefix + "_" + fieldName, fieldValue);

            if (fieldValue == null || !matchesCondition(fieldValue, condition)) {
                allMatch = false;
            }
        }

        features.put(prefix + "_matched", allMatch);
        return allMatch;
    }

    private boolean evaluateVelocityRule(EventDocument event, RuleEntity rule,
                                          Map<String, Object> features) {
        String prefix = sanitizeRuleName(rule.getName());
        String groupByField = rule.getGroupByField();
        Object groupValue = resolveField(event, groupByField, features);
        if (groupValue == null) {
            features.put(prefix + "_count", 0L);
            features.put(prefix + "_matched", false);
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

        features.put(prefix + "_count", count);
        features.put(prefix + "_matched", exceeded);

        if (exceeded) {
            log.info("Velocity rule '{}' fired: {}={} count={} threshold={}",
                rule.getName(), groupByField, groupValue,
                count, rule.getThreshold());
        }
        return exceeded;
    }

    private boolean matchesCondition(Object fieldValue, RuleCondition condition) {
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

    Object resolveField(EventDocument event, String field, Map<String, Object> features) {
        if (field.equals("eventType")) return event.eventType();

        if (field.startsWith("exports.")) {
            String exportKey = field.substring("exports.".length());
            return features.get(exportKey);
        }

        String attrKey = field.startsWith("attributes.") ? field.substring("attributes.".length()) : field;
        Map<String, Object> attrs = event.attributes();
        return attrs != null ? attrs.get(attrKey) : null;
    }

    static String sanitizeRuleName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
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
