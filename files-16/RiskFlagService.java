package com.example.fraud.risk;

import com.example.fraud.fraud.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Manages persistent fraud risk flags in the member-risk-flags ECH index.
 *
 * Used by:
 *  - AiFraudEngine: check flag before LLM call, write flag after BLOCK/HOLD
 *  - RiskFlagController: REST API for digital/ops/contact centre channels
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskFlagService {

    private final ElasticsearchOperations operations;

    private static final IndexCoordinates INDEX =
        IndexCoordinates.of("member-risk-flags");

    @Value("${fraud.risk-flag.expiry-days:30}")
    private int expiryDays;

    @Value("${fraud.risk-flag.min-score-to-flag:61}")
    private int minScoreToFlag;

    /**
     * Reads the active risk flag for a customer.
     * Returns empty if no flag exists or flag is expired/cleared.
     * This is the fast path — one ES _doc GET before LLM evaluation.
     */
    public Optional<MemberRiskFlag> getActiveFlag(String tenantId, String customerId) {
        try {
            MemberRiskFlag flag = operations.get(customerId, MemberRiskFlag.class, INDEX);
            if (flag != null && flag.isActive() && flag.tenantId().equals(tenantId)) {
                log.debug("Active risk flag found for customer {}: level={} score={}",
                    customerId, flag.riskLevel(), flag.riskScore());
                return Optional.of(flag);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.debug("No risk flag for customer {}: {}", customerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates or updates a risk flag based on the outcome of rule evaluation.
     * Only flags customers who scored above the minimum threshold (default 61 = BLOCK).
     */
    public Optional<MemberRiskFlag> createOrUpdateFlag(
            String tenantId,
            String customerId,
            String sourceEventId,
            List<FraudAlert> alerts,
            int compositeScore,
            String decision) {

        // Only flag BLOCK or HOLD decisions
        if (compositeScore < minScoreToFlag &&
            !decision.equals("BLOCK") && !decision.equals("FLAG")) {
            return Optional.empty();
        }

        if (alerts.isEmpty()) return Optional.empty();

        // Determine the highest severity alert
        FraudAlert primaryAlert = alerts.stream()
            .max(java.util.Comparator.comparingInt(FraudAlert::riskScore))
            .orElse(alerts.get(0));

        List<String> rulesFired = alerts.stream()
            .map(FraudAlert::ruleId)
            .toList();

        // Extract recommendation from primary alert reason
        String recommendation = extractRecommendation(primaryAlert.reason());
        if (recommendation == null) recommendation = decision.equals("BLOCK") ? "BLOCK" : "HOLD";

        Instant now = Instant.now();
        Instant expires = now.plus(expiryDays, ChronoUnit.DAYS);

        // Check if flag already exists — update rather than overwrite
        Optional<MemberRiskFlag> existing = getActiveFlag(tenantId, customerId);

        MemberRiskFlag flag;
        if (existing.isPresent()) {
            MemberRiskFlag prev = existing.get();
            // Keep the higher score and earliest flaggedAt
            int newScore = Math.max(prev.riskScore(), compositeScore);
            String newLevel = resolveHigherLevel(prev.riskLevel(), primaryAlert.severity());

            flag = new MemberRiskFlag(
                customerId, tenantId, newLevel, newScore,
                mergeLists(prev.rulesFired(), rulesFired),
                primaryAlert.reason(),
                recommendation,
                sourceEventId,
                prev.flaggedAt(),    // keep original flag date
                now,                 // update lastUpdatedAt
                expires,
                "ACTIVE", null, null, null
            );
            log.info("Updated risk flag for customer {}: level={} score={} rules={}",
                customerId, newLevel, newScore, rulesFired);
        } else {
            flag = new MemberRiskFlag(
                customerId, tenantId,
                primaryAlert.severity(), compositeScore,
                rulesFired,
                primaryAlert.reason(),
                recommendation,
                sourceEventId,
                now, now, expires,
                "ACTIVE", null, null, null
            );
            log.info("Created risk flag for customer {}: level={} score={} rules={}",
                customerId, primaryAlert.severity(), compositeScore, rulesFired);
        }

        try {
            operations.save(flag, INDEX);
            return Optional.of(flag);
        } catch (Exception e) {
            log.error("Failed to save risk flag for customer {}: {}",
                customerId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clears a risk flag — called by authorised analysts via the REST API.
     */
    public boolean clearFlag(String tenantId, String customerId,
                              String clearedBy, String reason) {
        Optional<MemberRiskFlag> existing = getActiveFlag(tenantId, customerId);
        if (existing.isEmpty()) return false;

        MemberRiskFlag prev = existing.get();
        MemberRiskFlag cleared = new MemberRiskFlag(
            customerId, tenantId,
            prev.riskLevel(), prev.riskScore(),
            prev.rulesFired(), prev.primaryReason(),
            prev.recommendation(), prev.sourceEventId(),
            prev.flaggedAt(), Instant.now(), prev.expiresAt(),
            "CLEARED", clearedBy, Instant.now(), reason
        );

        try {
            operations.save(cleared, INDEX);
            log.info("Risk flag cleared for customer {} by {} — reason: {}",
                customerId, clearedBy, reason);
            return true;
        } catch (Exception e) {
            log.error("Failed to clear risk flag for customer {}: {}",
                customerId, e.getMessage());
            return false;
        }
    }

    private String extractRecommendation(String reason) {
        if (reason == null) return null;
        for (String action : List.of("BLOCK", "HOLD", "INVESTIGATE", "STEP_UP", "ALLOW")) {
            if (reason.contains(action)) return action;
        }
        return null;
    }

    private String resolveHigherLevel(String a, String b) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        int ia = order.indexOf(a);
        int ib = order.indexOf(b);
        return ia >= ib ? a : b;
    }

    private List<String> mergeLists(List<String> a, List<String> b) {
        java.util.Set<String> merged = new java.util.LinkedHashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }
}
