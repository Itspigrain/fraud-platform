package com.example.fraud.api;

import com.example.fraud.risk.MemberRiskFlag;
import com.example.fraud.risk.RiskFlagService;
import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/risk-flags")
@RequiredArgsConstructor
public class RiskFlagController {

    private final RiskFlagService riskFlagService;

    @GetMapping("/{customerId}")
    public ResponseEntity<?> getFlag(@PathVariable String customerId) {
        String tenantId = TenantContext.getTenantId();
        Optional<MemberRiskFlag> flag = riskFlagService.getActiveFlag(tenantId, customerId);
        if (flag.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "customerId", customerId,
                "status", "CLEAN",
                "message", "No active risk flag for this customer",
                "checkedAt", Instant.now().toString()));
        }
        MemberRiskFlag f = flag.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("status", "FLAGGED");
        response.put("riskLevel", f.riskLevel());
        response.put("riskScore", f.riskScore());
        response.put("rulesFired", f.rulesFired());
        response.put("primaryReason", f.primaryReason() != null ? f.primaryReason() : "");
        response.put("recommendation", f.recommendation() != null ? f.recommendation() : "");
        response.put("flaggedAt", f.flaggedAt().toString());
        response.put("lastUpdatedAt", f.lastUpdatedAt().toString());
        response.put("expiresAt", f.expiresAt().toString());
        response.put("sourceEventId", f.sourceEventId() != null ? f.sourceEventId() : "");
        response.put("checkedAt", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{customerId}/clear")
    public ResponseEntity<?> clearFlag(@PathVariable String customerId,
                                        @RequestBody Map<String, String> body) {
        String tenantId = TenantContext.getTenantId();
        boolean cleared = riskFlagService.clearFlag(tenantId, customerId,
            body.getOrDefault("clearedBy", "unknown"),
            body.getOrDefault("reason", "No reason provided"));
        if (!cleared) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "customerId", customerId,
            "status", "NOT_FOUND",
            "message", "No active risk flag found to clear"));
        return ResponseEntity.ok(Map.of(
            "customerId", customerId,
            "status", "CLEARED",
            "clearedBy", body.getOrDefault("clearedBy", "unknown"),
            "clearedAt", Instant.now().toString()));
    }
}
