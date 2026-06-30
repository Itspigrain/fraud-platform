package com.example.fraud.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditEntry(
    String auditId,
    String tenantId,
    String eventId,
    List<String> rulesEvaluated,
    List<String> rulesFired,
    List<Map<String, String>> verdicts,
    Instant evaluatedAt
) {}
