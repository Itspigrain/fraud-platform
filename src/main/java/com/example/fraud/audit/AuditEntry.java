package com.example.fraud.audit;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;
import java.util.List;

public record AuditEntry(
    String auditId,
    String tenantId,
    String eventId,
    String customerId,
    List<String> rulesEvaluated,
    List<String> rulesFired,
    int compositeRiskScore,
    String decision,
    @Field(type = FieldType.Date) Instant evaluatedAt
) {}
