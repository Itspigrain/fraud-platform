package com.example.fraud.rule;

import java.time.Instant;
import java.util.List;

public record RuleResponse(
    Long id,
    String tenantId,
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
    Instant createdAt,
    Instant updatedAt
) {
    public static RuleResponse from(RuleEntity entity) {
        List<RuleCondition> conditions = entity.getConditions() != null
            ? entity.getParsedConditions()
            : List.of();
        return new RuleResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getEventType(),
            entity.getName(),
            entity.getDescription(),
            entity.getRuleType(),
            entity.getStatus(),
            conditions,
            entity.getGroupByField(),
            entity.getTimeWindowMinutes(),
            entity.getThreshold(),
            entity.getPromptTemplate(),
            entity.getEvaluationIntervalMinutes(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
