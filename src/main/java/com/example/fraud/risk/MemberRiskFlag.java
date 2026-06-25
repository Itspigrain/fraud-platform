package com.example.fraud.risk;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Document(indexName = "member-risk-flags", createIndex = false)
public record MemberRiskFlag(
    @Id String customerId,
    String tenantId,
    String riskLevel,
    int riskScore,
    List<String> rulesFired,
    String primaryReason,
    String recommendation,
    String sourceEventId,
    @Field(type = FieldType.Date) Instant flaggedAt,
    @Field(type = FieldType.Date) Instant lastUpdatedAt,
    @Field(type = FieldType.Date) Instant expiresAt,
    String status,
    String clearedBy,
    @Field(type = FieldType.Date) Instant clearedAt,
    String clearanceReason
) {
    public boolean isActive() {
        return "ACTIVE".equals(status) &&
               (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
}
