package com.example.fraud.rule;

public record RuleCondition(
    String field,
    ConditionOperator operator,
    String value
) {}
