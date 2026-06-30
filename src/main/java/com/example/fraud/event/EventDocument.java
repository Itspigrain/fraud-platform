package com.example.fraud.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import java.time.Instant;
import java.util.Map;

@Document(indexName = "events", createIndex = false)
public record EventDocument(
    @Id String id,
    String tenantId,
    String eventType,
    Instant eventTime,
    Map<String, Object> attributes,
    Map<String, Object> exportedFeatures
) {}
