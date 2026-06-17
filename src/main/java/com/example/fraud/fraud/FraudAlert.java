package com.example.fraud.fraud;

import java.time.Instant;

public record FraudAlert(
    String alertId,
    String eventId,
    String customerId,
    String ruleId,
    String severity,
    int riskScore,
    String reason,
    Instant detectedAt
) {}
