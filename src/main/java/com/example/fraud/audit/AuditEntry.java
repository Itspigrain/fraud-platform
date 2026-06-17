package com.example.fraud.audit;

import java.time.Instant;
import java.util.List;

public record AuditEntry(
    String auditId,
    String eventId,
    String customerId,
    List<String> rulesEvaluated,
    List<String> rulesFired,
    int compositeRiskScore,
    String decision,
    Instant evaluatedAt
) {}
