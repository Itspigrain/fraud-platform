package com.example.fraud.risk;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

/**
 * Persistent fraud risk flag stored against a member/customer in ECH.
 *
 * Survives across sessions, channels, and downstream systems.
 * Available to digital, operations, and contact centre channels via REST API.
 *
 * Flag lifecycle:
 *  - Created when a BLOCK or HOLD decision is made by the AI engine
 *  - Updated when additional rules fire for the same customer
 *  - Expires automatically after configurable period (default 30 days)
 *  - Removed by authorised analyst action via DELETE /risk-flags/{customerId}
 */
@Document(indexName = "member-risk-flags", createIndex = false)
public record MemberRiskFlag(

    @Id
    String customerId,           // primary key — one flag per customer

    String tenantId,

    String riskLevel,            // CRITICAL / HIGH / MEDIUM / LOW

    int riskScore,               // composite score that triggered the flag

    List<String> rulesFired,     // which rules contributed to this flag

    String primaryReason,        // LLM-generated reason from the triggering event

    String recommendation,       // BLOCK / HOLD / INVESTIGATE / STEP_UP

    String sourceEventId,        // event that triggered the flag

    @Field(type = FieldType.Date)
    Instant flaggedAt,           // when the flag was first set

    @Field(type = FieldType.Date)
    Instant lastUpdatedAt,       // when the flag was most recently updated

    @Field(type = FieldType.Date)
    Instant expiresAt,           // auto-expiry (default: 30 days)

    String status,               // ACTIVE / EXPIRED / CLEARED

    String clearedBy,            // analyst who cleared it (null if still active)

    @Field(type = FieldType.Date)
    Instant clearedAt,           // when it was cleared (null if still active)

    String clearanceReason       // reason for clearance (null if still active)

) {
    public boolean isActive() {
        return "ACTIVE".equals(status) &&
               (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
}
