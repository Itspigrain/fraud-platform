package com.example.fraud.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventSchemaEntityTest {

    @Test
    void shouldRoundTripFields() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, "Customer ID"),
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, "Transaction amount"),
            new SchemaFieldDefinition("notes", SchemaFieldType.TEXT, false, null)
        );

        var entity = new EventSchemaEntity();
        entity.setFieldsFromList(fields);

        List<SchemaFieldDefinition> parsed = entity.getParsedFields();
        assertThat(parsed).hasSize(3);
        assertThat(parsed.get(0).name()).isEqualTo("customerId");
        assertThat(parsed.get(0).type()).isEqualTo(SchemaFieldType.KEYWORD);
        assertThat(parsed.get(0).required()).isTrue();
        assertThat(parsed.get(1).type()).isEqualTo(SchemaFieldType.DOUBLE);
        assertThat(parsed.get(2).required()).isFalse();
    }

    @Test
    void schemaFieldTypeShouldMapToEsType() {
        assertThat(SchemaFieldType.KEYWORD.toEsType()).isEqualTo("keyword");
        assertThat(SchemaFieldType.GEO_POINT.toEsType()).isEqualTo("geo_point");
        assertThat(SchemaFieldType.DOUBLE.toEsType()).isEqualTo("double");
    }
}
