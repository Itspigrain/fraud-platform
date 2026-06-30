package com.example.fraud.rule;

import com.example.fraud.schema.SchemaFieldDefinition;
import com.example.fraud.schema.SchemaFieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleValidationServiceTest {

    private final RuleValidationService validator = new RuleValidationService();

    private final List<SchemaFieldDefinition> purchaseSchema = List.of(
        new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null),
        new SchemaFieldDefinition("country", SchemaFieldType.KEYWORD, true, null),
        new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, null),
        new SchemaFieldDefinition("description", SchemaFieldType.TEXT, false, null),
        new SchemaFieldDefinition("quantity", SchemaFieldType.INTEGER, false, null)
    );

    private RuleRequest conditionRequest(List<RuleCondition> conditions) {
        return new RuleRequest("purchase", "test-rule", null,
            RuleType.CONDITION, RuleStatus.ACTIVE,
            conditions, null, null, null, null, null, null, null, null, null);
    }

    private RuleRequest velocityRequest(String groupByField) {
        return new RuleRequest("purchase", "test-velocity", null,
            RuleType.VELOCITY, RuleStatus.ACTIVE,
            null, groupByField, 10, 5, null, null, null, null, null, null);
    }

    @Test
    void validConditionRulePassesValidation() {
        var request = conditionRequest(List.of(
            new RuleCondition("amount", ConditionOperator.GREATER_THAN, "1000"),
            new RuleCondition("country", ConditionOperator.EQUALS, "NG")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void unknownFieldNameReturnsError() {
        var request = conditionRequest(List.of(
            new RuleCondition("amuont", ConditionOperator.GREATER_THAN, "1000")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("amuont");
        assertThat(errors.get(0).message()).contains("not defined in the schema");
    }

    @Test
    void attributesPrefixIsStrippedBeforeValidation() {
        var request = conditionRequest(List.of(
            new RuleCondition("attributes.amount", ConditionOperator.GREATER_THAN, "1000")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void numericOperatorOnNonNumericFieldReturnsError() {
        var request = conditionRequest(List.of(
            new RuleCondition("country", ConditionOperator.GREATER_THAN, "100")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("not compatible with field type KEYWORD");
    }

    @Test
    void numericOperatorWithNonNumericValueReturnsError() {
        var request = conditionRequest(List.of(
            new RuleCondition("amount", ConditionOperator.GREATER_THAN, "abc")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("not a valid number");
    }

    @Test
    void impossibleEqualsConditionsDetected() {
        var request = conditionRequest(List.of(
            new RuleCondition("country", ConditionOperator.EQUALS, "NG"),
            new RuleCondition("country", ConditionOperator.EQUALS, "US")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("Impossible condition");
        assertThat(errors.get(0).message()).contains("NG");
        assertThat(errors.get(0).message()).contains("US");
    }

    @Test
    void sameEqualsValueIsNotImpossible() {
        var request = conditionRequest(List.of(
            new RuleCondition("country", ConditionOperator.EQUALS, "NG"),
            new RuleCondition("country", ConditionOperator.EQUALS, "NG")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void builtinFieldEventTypeIsAlwaysValid() {
        var request = conditionRequest(List.of(
            new RuleCondition("eventType", ConditionOperator.EQUALS, "purchase")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void velocityRuleWithValidGroupByFieldPasses() {
        var request = velocityRequest("customerId");

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void velocityRuleWithUnknownGroupByFieldReturnsError() {
        var request = velocityRequest("userIp");

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("groupByField");
        assertThat(errors.get(0).message()).contains("not defined in the schema");
    }

    @Test
    void velocityRuleWithBlankGroupByFieldReturnsError() {
        var request = velocityRequest("");

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("required");
    }

    @Test
    void llmEvaluatorRuleSkipsValidation() {
        var request = new RuleRequest("purchase", "llm-rule", null,
            RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE,
            null, null, 30, null, "Analyze for fraud", 5, null, null, null, null);

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }

    @Test
    void blankConditionFieldNameReturnsError() {
        var request = conditionRequest(List.of(
            new RuleCondition("", ConditionOperator.EQUALS, "test")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("blank");
    }

    @Test
    void multipleErrorsReturnedTogether() {
        var request = conditionRequest(List.of(
            new RuleCondition("typo_field", ConditionOperator.EQUALS, "x"),
            new RuleCondition("country", ConditionOperator.GREATER_THAN, "abc")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).hasSize(3);
    }

    @Test
    void integerFieldAcceptsNumericOperators() {
        var request = conditionRequest(List.of(
            new RuleCondition("quantity", ConditionOperator.GREATER_THAN, "10")
        ));

        var errors = validator.validate(request, purchaseSchema);

        assertThat(errors).isEmpty();
    }
}
