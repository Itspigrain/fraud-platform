package com.example.fraud.fraud;

import com.example.fraud.audit.AuditEntry;
import java.util.List;

public record EvaluationResult(
    List<FraudAlert> alerts,
    AuditEntry audit
) {}
