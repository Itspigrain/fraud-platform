package com.example.fraud.connector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConnectorResponse(
    Long id,
    String tenantId,
    String name,
    String description,
    ConnectorType type,
    ConnectorStatus status,
    Map<String, Object> config,
    List<Long> ruleIds,
    Integer retryAttempts,
    Integer retryDelayMs,
    Instant createdAt,
    Instant updatedAt
) {
    public static ConnectorResponse from(ConnectorEntity entity) {
        return new ConnectorResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getName(),
            entity.getDescription(),
            entity.getType(),
            entity.getStatus(),
            entity.getParsedConfig(),
            entity.getParsedRuleIds(),
            entity.getRetryAttempts(),
            entity.getRetryDelayMs(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
