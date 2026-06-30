package com.example.fraud.rule;

import java.util.List;

public record RuleRequest(
    String eventType,
    String name,
    String description,
    RuleType ruleType,
    RuleStatus status,
    List<RuleCondition> conditions,
    String groupByField,
    Integer timeWindowMinutes,
    Integer threshold,
    String promptTemplate,
    Integer evaluationIntervalMinutes,
    String verdict,
    String severity,
    List<Long> dependsOn,
    DependencyCondition dependencyCondition
) {}
