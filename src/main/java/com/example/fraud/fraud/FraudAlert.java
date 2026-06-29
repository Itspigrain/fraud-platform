package com.example.fraud.fraud;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;

public record FraudAlert(
    String alertId,
    String tenantId,
    String eventId,
    String customerId,
    String ruleId,
    String severity,
    int riskScore,
    String reason,
    @Field(type = FieldType.Date) Instant detectedAt
) {}
