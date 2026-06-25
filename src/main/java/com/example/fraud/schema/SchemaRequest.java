package com.example.fraud.schema;

import java.util.List;

public record SchemaRequest(
    String eventType,
    String displayName,
    String description,
    List<SchemaFieldDefinition> fields
) {}
