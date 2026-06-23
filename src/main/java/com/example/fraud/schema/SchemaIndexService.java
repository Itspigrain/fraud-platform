package com.example.fraud.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaIndexService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    public static String tenantIndexName(String tenantId) {
        return "events-" + tenantId;
    }

    public void ensureTenantIndex(String tenantId, List<SchemaFieldDefinition> fields) {
        String name = tenantIndexName(tenantId);
        IndexCoordinates index = IndexCoordinates.of(name);

        if (!elasticsearchTemplate.indexOps(index).exists()) {
            elasticsearchTemplate.indexOps(index).create();
            log.info("Created ES index: {}", name);
        }

        Map<String, Object> mapping = buildMappingProperties(fields);
        elasticsearchTemplate.indexOps(index).putMapping(Document.from(mapping));
        log.info("Updated mapping for index: {} with {} attribute fields", name, fields.size());
    }

    public static Map<String, Object> buildMappingProperties(List<SchemaFieldDefinition> fields) {
        Map<String, Object> attrProperties = new LinkedHashMap<>();
        for (SchemaFieldDefinition field : fields) {
            attrProperties.put(field.name(), Map.of("type", field.type().toEsType()));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("tenantId", Map.of("type", "keyword"));
        properties.put("eventType", Map.of("type", "keyword"));
        properties.put("eventTime", Map.of("type", "date"));
        properties.put("attributes", Map.of(
            "type", "object",
            "dynamic", "strict",
            "properties", attrProperties
        ));

        return Map.of("properties", properties);
    }
}
