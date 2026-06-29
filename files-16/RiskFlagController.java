package com.example.fraud.api;

import com.example.fraud.risk.MemberRiskFlag;
import com.example.fraud.risk.RiskFlagService;
import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for persistent member risk flags.
 * Available to digital, operations, and contact centre channels.
 *
 * GET    /risk-flags/{customerId}              — check if customer is flagged
 * POST   /risk-flags/{customerId}/clear        — analyst clears a flag
 * GET    /risk-flags/{customerId}/history      — flag history (TODO: audit trail)
 */
@RestController
@RequestMapping("/risk-flags")
@RequiredArgsConstructor
public class RiskFlagController {

    private final RiskFlagService riskFlagService;

    /**
     * Check if a customer has an active risk flag.
     * Returns 200 with flag details if flagged, 404 if clean.
     *
     * Used by:
     *  - Digital channel: check before allowing high-risk transactions
     *  - Contact centre: display risk status when customer calls
     *  - Operations: investigation queue
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<?> getFlag(@PathVariable String customerId) {
        String tenantId = TenantContext.getTenantId();
        Optional<MemberRiskFlag> flag = riskFlagService.getActiveFlag(tenantId, customerId);

        if (flag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "customerId", customerId,
                "status", "CLEAN",
                "message", "No active risk flag for this customer",
                "checkedAt", Instant.now().toString()
            ));
        }

        MemberRiskFlag f = flag.get();
        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "status", "FLAGGED",
            "riskLevel", f.riskLevel(),
            "riskScore", f.riskScore(),
            "rulesFired", f.rulesFired(),
            "primaryReason", f.primaryReason() != null ? f.primaryReason() : "",
            "recommendation", f.recommendation() != null ? f.recommendation() : "",
            "flaggedAt", f.flaggedAt().toString(),
            "lastUpdatedAt", f.lastUpdatedAt().toString(),
            "expiresAt", f.expiresAt().toString(),
            "sourceEventId", f.sourceEventId() != null ? f.sourceEventId() : "",
            "checkedAt", Instant.now().toString()
        ));
    }

    /**
     * Clear a risk flag — authorised analyst action only.
     * Requires clearedBy and reason in the request body.
     */
    @PostMapping("/{customerId}/clear")
    public ResponseEntity<?> clearFlag(
            @PathVariable String customerId,
            @RequestBody Map<String, String> body) {

        String tenantId = TenantContext.getTenantId();
        String clearedBy = body.getOrDefault("clearedBy", "unknown");
        String reason = body.getOrDefault("reason", "No reason provided");

        boolean cleared = riskFlagService.clearFlag(
            tenantId, customerId, clearedBy, reason);

        if (!cleared) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "customerId", customerId,
                "status", "NOT_FOUND",
                "message", "No active risk flag found to clear"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "status", "CLEARED",
            "clearedBy", clearedBy,
            "reason", reason,
            "clearedAt", Instant.now().toString()
        ));
    }
}
