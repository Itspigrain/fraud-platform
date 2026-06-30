package com.example.fraud.alert;

import java.time.Instant;

public record Alert(
    String alertId,
    String tenantId,
    String eventId,
    String ruleId,
    String severity,
    String verdict,
    String reason,
    Instant detectedAt
) {}
