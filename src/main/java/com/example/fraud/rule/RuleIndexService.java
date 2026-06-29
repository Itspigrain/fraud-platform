package com.example.fraud.rule;

import com.example.fraud.event.EventDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleIndexService {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final ObjectMapper objectMapper;

    public String indexName(String tenantId, Long ruleId) {
        return "rule-results-" + tenantId + "-" + ruleId;
    }

    public void createIndex(String tenantId, Long ruleId) {
        String name = indexName(tenantId, ruleId);
        IndexCoordinates index = IndexCoordinates.of(name);

        if (!elasticsearchTemplate.indexOps(index).exists()) {
            elasticsearchTemplate.indexOps(index).create();
            elasticsearchTemplate.indexOps(index).putMapping(Document.from(eventMapping()));
            log.info("Created ES index: {}", name);
        }
    }

    public void deleteIndex(String tenantId, Long ruleId) {
        String name = indexName(tenantId, ruleId);
        IndexCoordinates index = IndexCoordinates.of(name);

        if (elasticsearchTemplate.indexOps(index).exists()) {
            elasticsearchTemplate.indexOps(index).delete();
            log.info("Deleted ES index: {}", name);
        }
    }

    public void indexEvent(String tenantId, Long ruleId, EventDocument event) {
        String name = indexName(tenantId, ruleId);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = objectMapper.convertValue(event, Map.class);
        IndexQuery query = new IndexQueryBuilder()
            .withId(event.id())
            .withObject(source)
            .build();
        elasticsearchTemplate.index(query, IndexCoordinates.of(name));
    }

    private Map<String, Object> eventMapping() {
        return Map.of("properties", Map.ofEntries(
            Map.entry("id", Map.of("type", "keyword")),
            Map.entry("tenantId", Map.of("type", "keyword")),
            Map.entry("eventType", Map.of("type", "keyword")),
            Map.entry("eventTime", Map.of("type", "date")),
            Map.entry("attributes", Map.of("type", "object", "dynamic", true))
        ));
    }
}
