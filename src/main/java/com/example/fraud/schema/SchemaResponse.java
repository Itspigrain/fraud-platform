package com.example.fraud.schema;

import java.time.Instant;
import java.util.List;

public record SchemaResponse(
    Long id,
    String tenantId,
    String eventType,
    String displayName,
    String description,
    List<SchemaFieldDefinition> fields,
    Instant createdAt,
    Instant updatedAt
) {
    public static SchemaResponse from(EventSchemaEntity entity) {
        return new SchemaResponse(
            entity.getId(),
            entity.getTenantId(),
            entity.getEventType(),
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getParsedFields(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
