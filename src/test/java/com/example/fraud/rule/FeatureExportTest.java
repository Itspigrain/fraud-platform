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
class FeatureExportTest {

    @Mock private ElasticsearchTemplate elasticsearchTemplate;
    @InjectMocks private RuleEvaluationService evaluationService;

    private EventDocument event(Map<String, Object> attrs) {
        return new EventDocument("e1", "t1", "purchase", Instant.now(), attrs, null);
    }

    private RuleEntity conditionRule(Long id, String name, String field,
                                      ConditionOperator op, String value) {
        RuleEntity rule = new RuleEntity();
        rule.setId(id);
        rule.setName(name);
        rule.setTenantId("t1");
        rule.setEventType("purchase");
        rule.setRuleType(RuleType.CONDITION);
        rule.setConditionsFromList(List.of(new RuleCondition(field, op, value)));
        return rule;
    }

    @Test
    void conditionRuleExportsMatchedAndFieldValues() {
        RuleEntity rule = conditionRule(1L, "geo_check", "country",
            ConditionOperator.EQUALS, "NG");
        EventDocument evt = event(Map.of("country", "NG"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(rule));

        assertThat(result.matchedRules()).hasSize(1);
        assertThat(result.exportedFeatures())
            .containsEntry("geo_check_matched", true)
            .containsEntry("geo_check_country", "NG");
    }

    @Test
    void conditionRuleExportsFalseWhenNotMatched() {
        RuleEntity rule = conditionRule(1L, "geo_check", "country",
            ConditionOperator.EQUALS, "NG");
        EventDocument evt = event(Map.of("country", "US"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(rule));

        assertThat(result.matchedRules()).isEmpty();
        assertThat(result.exportedFeatures())
            .containsEntry("geo_check_matched", false)
            .containsEntry("geo_check_country", "US");
    }

    @Test
    void conditionRuleWithMultipleConditionsExportsAllFieldValues() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.setName("purchase_check");
        rule.setTenantId("t1");
        rule.setEventType("purchase");
        rule.setRuleType(RuleType.CONDITION);
        rule.setConditionsFromList(List.of(
            new RuleCondition("country", ConditionOperator.EQUALS, "NG"),
            new RuleCondition("amount", ConditionOperator.GREATER_THAN, "1000")
        ));

        EventDocument evt = event(Map.of("country", "NG", "amount", "5000"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(rule));

        assertThat(result.exportedFeatures())
            .containsEntry("purchase_check_matched", true)
            .containsEntry("purchase_check_country", "NG")
            .containsEntry("purchase_check_amount", "5000");
    }

    @Test
    void ruleNameIsSanitizedForFeatureKeys() {
        RuleEntity rule = conditionRule(1L, "High Value Check", "country",
            ConditionOperator.EQUALS, "NG");
        EventDocument evt = event(Map.of("country", "NG"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(rule));

        assertThat(result.exportedFeatures())
            .containsKey("high_value_check_matched")
            .containsKey("high_value_check_country");
    }

    @Test
    void unmatchedRuleStillExportsFeatures() {
        RuleEntity rule = conditionRule(1L, "geo_check", "country",
            ConditionOperator.EQUALS, "NG");
        EventDocument evt = event(Map.of("country", "US"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(rule));

        assertThat(result.matchedRules()).isEmpty();
        assertThat(result.exportedFeatures()).isNotEmpty();
        assertThat(result.exportedFeatures()).containsEntry("geo_check_country", "US");
    }

    @Test
    void noRulesProducesEmptyFeatures() {
        EventDocument evt = event(Map.of("country", "NG"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of());

        assertThat(result.matchedRules()).isEmpty();
        assertThat(result.exportedFeatures()).isEmpty();
    }

    @Test
    void skippedDependencyRuleDoesNotExportFeatures() {
        RuleEntity ruleA = conditionRule(1L, "prereq", "country",
            ConditionOperator.EQUALS, "NG");

        RuleEntity ruleB = conditionRule(2L, "downstream", "amount",
            ConditionOperator.GREATER_THAN, "1000");
        ruleB.setDependsOnFromList(List.of(1L));
        ruleB.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "US", "amount", "5000"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(ruleA, ruleB));

        assertThat(result.exportedFeatures()).containsKey("prereq_matched");
        assertThat(result.exportedFeatures()).doesNotContainKey("downstream_matched");
    }

    // Downstream chaining tests

    @Test
    void downstreamRuleReferencesUpstreamExports() {
        RuleEntity ruleA = conditionRule(1L, "geo_check", "country",
            ConditionOperator.EQUALS, "NG");

        RuleEntity ruleB = new RuleEntity();
        ruleB.setId(2L);
        ruleB.setName("flagged_geo");
        ruleB.setTenantId("t1");
        ruleB.setEventType("purchase");
        ruleB.setRuleType(RuleType.CONDITION);
        ruleB.setConditionsFromList(List.of(
            new RuleCondition("exports.geo_check_matched", ConditionOperator.EQUALS, "true")
        ));
        ruleB.setDependsOnFromList(List.of(1L));
        ruleB.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "NG"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(ruleA, ruleB));

        assertThat(result.matchedRules()).extracting(RuleEntity::getId).containsExactly(1L, 2L);
        assertThat(result.exportedFeatures())
            .containsEntry("geo_check_matched", true)
            .containsEntry("flagged_geo_matched", true);
    }

    @Test
    void downstreamRuleDoesNotMatchWhenUpstreamExportFails() {
        RuleEntity ruleA = conditionRule(1L, "geo_check", "country",
            ConditionOperator.EQUALS, "NG");

        RuleEntity ruleB = new RuleEntity();
        ruleB.setId(2L);
        ruleB.setName("flagged_geo");
        ruleB.setTenantId("t1");
        ruleB.setEventType("purchase");
        ruleB.setRuleType(RuleType.CONDITION);
        ruleB.setConditionsFromList(List.of(
            new RuleCondition("exports.geo_check_matched", ConditionOperator.EQUALS, "true")
        ));
        ruleB.setDependsOnFromList(List.of(1L));
        ruleB.setDependencyCondition(DependencyCondition.ALL);

        EventDocument evt = event(Map.of("country", "US"));

        EvaluationResult result = evaluationService.evaluate(evt, List.of(ruleA, ruleB));

        assertThat(result.matchedRules()).isEmpty();
    }
}
