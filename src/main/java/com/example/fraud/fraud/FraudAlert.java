package com.example.fraud.fraud;

import java.time.Instant;

public record FraudAlert(
    String alertId,
    String tenantId,
    String eventId,
    String ruleId,
    String severity,
    String reason,
    Instant detectedAt
) {}
