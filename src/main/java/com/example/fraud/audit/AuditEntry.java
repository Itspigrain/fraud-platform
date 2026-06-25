package com.example.fraud.audit;

import java.time.Instant;
import java.util.List;

public record AuditEntry(
    String auditId,
    String tenantId,
    String eventId,
    List<String> rulesEvaluated,
    List<String> rulesFired,
    String decision,
    Instant evaluatedAt
) {}
