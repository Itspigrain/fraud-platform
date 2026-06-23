package com.example.fraud.rule;

import java.util.List;

public record RuleRequest(
    String name,
    String description,
    RuleType ruleType,
    RuleStatus status,
    List<RuleCondition> conditions,
    String groupByField,
    Integer timeWindowMinutes,
    Integer threshold
) {}
