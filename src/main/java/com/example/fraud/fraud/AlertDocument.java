package com.example.fraud.fraud;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import java.time.Instant;

@Document(indexName = "alerts", createIndex = false)
public record AlertDocument(
    @Id String alertId,
    String tenantId,
    String eventId,
    String ruleId,
    String severity,
    String reason,
    Instant detectedAt
) {}
