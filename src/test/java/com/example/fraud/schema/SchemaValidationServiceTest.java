package com.example.fraud.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidationServiceTest {

    private SchemaValidationService service;

    @BeforeEach
    void setUp() {
        service = new SchemaValidationService();
    }

    @Test
    void validateSchemaDefinition_rejectsDuplicateFieldNames() {
        var fields = List.of(
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null),
            new SchemaFieldDefinition("amount", SchemaFieldType.INTEGER, false, null)
        );
        assertThatThrownBy(() -> service.validateSchemaDefinition(fields))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Duplicate field name");
    }

    @Test
    void validateAttributes_passesValidData() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, null),
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null),
            new SchemaFieldDefinition("active", SchemaFieldType.BOOLEAN, false, null)
        );
        var attrs = Map.<String, Object>of("customerId", "cust-1", "amount", 99.5, "active", true);
        var violations = service.validateAttributes(attrs, fields);
        assertThat(violations).isEmpty();
    }

    @Test
    void validateAttributes_reportsMissingRequiredField() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, null)
        );
        var violations = service.validateAttributes(Map.of(), fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).field()).isEqualTo("customerId");
        assertThat(violations.get(0).reason()).contains("required");
    }

    @Test
    void validateAttributes_reportsTypeMismatch() {
        var fields = List.of(
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null)
        );
        var violations = service.validateAttributes(Map.of("amount", "not-a-number"), fields);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).field()).isEqualTo("amount");
        assertThat(violations.get(0).reason()).contains("double");
    }

    @Test
    void validateAttributes_acceptsIntegerForDoubleField() {
        var fields = List.of(
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null)
        );
        var violations = service.validateAttributes(Map.of("amount", 100), fields);
        assertThat(violations).isEmpty();
    }

    @Test
    void validateAttributes_reportsInvalidIp() {
        var fields = List.of(
            new SchemaFieldDefinition("sourceIp", SchemaFieldType.IP, true, null)
        );
        var violations = service.validateAttributes(Map.of("sourceIp", "not-an-ip"), fields);
        assertThat(violations).hasSize(1);
    }

    @Test
    void stripUnknownFields_removesExtraFields() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, null)
        );
        var attrs = Map.<String, Object>of("customerId", "cust-1", "extraField", "ignored");
        var stripped = service.stripUnknownFields(attrs, fields);
        assertThat(stripped).containsOnlyKeys("customerId");
    }
}
