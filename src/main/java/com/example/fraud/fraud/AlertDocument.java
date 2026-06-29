package com.example.fraud.fraud;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;

@Document(indexName = "alerts", createIndex = false)
public record AlertDocument(
    @Id String alertId,
    String tenantId,
    String eventId,
    String customerId,
    String ruleId,
    String severity,
    int riskScore,
    String reason,
    @Field(type = FieldType.Date) Instant detectedAt
) {}
