package com.example.fraud.schema;

public record SchemaFieldDefinition(
    String name,
    SchemaFieldType type,
    boolean required,
    String description
) {}
