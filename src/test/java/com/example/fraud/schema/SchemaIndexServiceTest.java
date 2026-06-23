package com.example.fraud.schema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIndexServiceTest {

    @Test
    void tenantIndexNameShouldFollowConvention() {
        assertThat(SchemaIndexService.tenantIndexName("acme")).isEqualTo("events-acme");
        assertThat(SchemaIndexService.tenantIndexName("bank-co")).isEqualTo("events-bank-co");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMappingPropertiesShouldGenerateCorrectStructure() {
        var fields = List.of(
            new SchemaFieldDefinition("customerId", SchemaFieldType.KEYWORD, true, null),
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null),
            new SchemaFieldDefinition("location", SchemaFieldType.GEO_POINT, false, null)
        );

        Map<String, Object> mapping = SchemaIndexService.buildMappingProperties(fields);

        assertThat(mapping).containsKey("properties");
        var props = (Map<String, Object>) mapping.get("properties");

        assertThat(props).containsKeys("id", "tenantId", "eventType", "eventTime", "attributes");

        var attrs = (Map<String, Object>) props.get("attributes");
        assertThat(attrs.get("type")).isEqualTo("object");
        assertThat(attrs.get("dynamic")).isEqualTo("strict");

        var attrProps = (Map<String, Object>) attrs.get("properties");
        assertThat(attrProps).containsKeys("customerId", "amount", "location");

        var customerIdMapping = (Map<String, Object>) attrProps.get("customerId");
        assertThat(customerIdMapping.get("type")).isEqualTo("keyword");

        var locationMapping = (Map<String, Object>) attrProps.get("location");
        assertThat(locationMapping.get("type")).isEqualTo("geo_point");
    }
}
