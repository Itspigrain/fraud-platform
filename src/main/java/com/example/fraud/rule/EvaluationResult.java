package com.example.fraud.rule;

import java.util.List;
import java.util.Map;

public record EvaluationResult(
    List<RuleEntity> matchedRules,
    Map<String, Object> exportedFeatures
) {}
