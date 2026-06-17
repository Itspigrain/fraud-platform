package com.example.fraud.fraud;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import java.time.Instant;

@Document(indexName = "alerts", createIndex = false)
public record AlertDocument(
    @Id String alertId,
    String eventId,
    String customerId,
    String ruleId,
    String severity,
    int riskScore,
    String reason,
    Instant detectedAt
) {}
