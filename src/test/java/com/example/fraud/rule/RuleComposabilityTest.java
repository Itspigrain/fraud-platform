package com.example.fraud.rule;

import com.example.fraud.event.EventDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RuleComposabilityTest {

    @Mock private ElasticsearchTemplate elasticsearchTemplate;
    @InjectMocks private RuleEvaluationService evaluationService;

    private RuleEntity conditionRule(Long id, String field, String value) {
        return conditionRule(id, field, ConditionOperator.EQUALS, value);
    }

    private RuleEntity conditionRule(Long id, String field, ConditionOperator op, String value) {
        RuleEntity rule = new RuleEntity();
        rule.setId(id);
        rule.setTenantId("t1");
        rule.setEventType("purchase");
        rule.setRuleType(RuleType.CONDITION);
        rule.setConditionsFromList(List.of(new RuleCondition(field, op, value)));
        return rule;
    }

    private EventDocument event(Map<String, Object> attrs) {
        return new EventDocument("e1", "t1", "purchase", Instant.now(), attrs);
    }

    @Test
    void ruleWithNoDependenciesEvaluatesNormally() {
        RuleEntity rule = conditionRule(1L, "country", "NG");
        EventDocument evt = event(Map.of("country", "NG"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(rule));

        assertThat(matched).containsExactly(rule);
    }

    @Test
    void ruleWithDependsOnAllFiresWhenAllDependenciesMatch() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(1L, 2L));
        ruleC.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "NG", "amount", "5000", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB, ruleC));

        assertThat(matched).extracting(RuleEntity::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void ruleWithDependsOnAllSkippedWhenOneDependencyDoesNotMatch() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(1L, 2L));
        ruleC.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "NG", "amount", "500", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB, ruleC));

        assertThat(matched).extracting(RuleEntity::getId).containsExactly(1L);
    }

    @Test
    void ruleWithDependsOnAnyFiresWhenAtLeastOneDependencyMatches() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(1L, 2L));
        ruleC.setDependencyCondition(DependencyCondition.ANY);

        EventDocument evt = event(Map.of("country", "NG", "amount", "500", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB, ruleC));

        assertThat(matched).extracting(RuleEntity::getId).containsExactly(1L, 3L);
    }

    @Test
    void ruleWithDependsOnAnySkippedWhenNoDependencyMatches() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(1L, 2L));
        ruleC.setDependencyCondition(DependencyCondition.ANY);

        EventDocument evt = event(Map.of("country", "US", "amount", "500", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB, ruleC));

        assertThat(matched).isEmpty();
    }

    @Test
    void topologicalSortPutsDependenciesFirst() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "0");
        ruleB.setDependsOnFromList(List.of(1L));

        List<RuleEntity> sorted = evaluationService.topologicalSort(List.of(ruleB, ruleA));

        assertThat(sorted).extracting(RuleEntity::getId).containsExactly(1L, 2L);
    }

    @Test
    void dependencyChainEvaluatesInOrder() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");

        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");
        ruleB.setDependsOnFromList(List.of(1L));
        ruleB.setDependencyCondition(DependencyCondition.ALL);

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(2L));
        ruleC.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "NG", "amount", "5000", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleC, ruleA, ruleB));

        assertThat(matched).extracting(RuleEntity::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void defaultDependencyConditionIsAll() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        RuleEntity ruleC = conditionRule(3L, "currency", "NGN");
        ruleC.setDependsOnFromList(List.of(1L, 2L));
        // dependencyCondition left null — should default to ALL

        EventDocument evt = event(Map.of("country", "NG", "amount", "500", "currency", "NGN"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB, ruleC));

        assertThat(matched).extracting(RuleEntity::getId).containsExactly(1L);
    }

    @Test
    void rulesWithoutDependenciesAreUnaffected() {
        RuleEntity ruleA = conditionRule(1L, "country", "NG");
        RuleEntity ruleB = conditionRule(2L, "amount", ConditionOperator.GREATER_THAN, "1000");

        EventDocument evt = event(Map.of("country", "NG", "amount", "5000"));

        List<RuleEntity> matched = evaluationService.evaluate(evt, List.of(ruleA, ruleB));

        assertThat(matched).extracting(RuleEntity::getId).containsExactlyInAnyOrder(1L, 2L);
    }
}
