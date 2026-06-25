package com.example.fraud.ai;

import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.FraudAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Evaluates ALL fraud rules in a single LLM inference call.
 *
 * Cost comparison:
 *   Before: 8 rules × 1 call each = 8 calls × ~1,450 tokens = ~11,600 tokens/event
 *   After:  1 call × all rules    = 1 call × ~2,500 tokens  = ~2,500 tokens/event
 *
 * The LLM receives all rule definitions + event context in one prompt,
 * and returns a JSON array with one result per rule.
 */
@Slf4j
public class MultiRuleEvaluator {

    private final RestClient inferenceClient;
    private final ObjectMapper objectMapper;
    private final String inferenceEndpointId;

    public MultiRuleEvaluator(RestClient inferenceClient,
                               ObjectMapper objectMapper,
                               String inferenceEndpointId) {
        this.inferenceClient = inferenceClient;
        this.objectMapper = objectMapper;
        this.inferenceEndpointId = inferenceEndpointId;
    }

    /**
     * Evaluates all rules in a single inference call.
     * Returns a list of FraudAlert for each rule that fired.
     */
    public List<FraudAlert> evaluateAll(EventDocument event,
                                         CustomerContext ctx,
                                         List<RuleDefinition> rules) {
        if (rules.isEmpty()) return List.of();

        String prompt = buildPrompt(event, ctx, rules);

        try {
            String raw = callInference(prompt);
            if (raw == null) return List.of();

            log.debug("Multi-rule raw response (first 500 chars): {}",
                raw.length() > 500 ? raw.substring(0, 500) : raw);

            List<Map<String, Object>> results = parseResults(raw, rules);
            return buildAlerts(event, rules, results);

        } catch (Exception e) {
            log.error("Multi-rule evaluation failed for customer {}: {}",
                event.customerId(), e.getMessage());
            return List.of();
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(EventDocument event,
                                CustomerContext ctx,
                                List<RuleDefinition> rules) {

        // Format all rule definitions compactly
        StringBuilder ruleBlock = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            RuleDefinition r = rules.get(i);
            ruleBlock.append(String.format(
                "RULE %d: id=%s severity=%s score=%d%n  %s%n%n",
                i + 1, r.id(), r.severity(), r.score(), r.prompt()
            ));
        }

        // Format rule IDs for the response template
        String ruleIds = rules.stream()
            .map(r -> "\"" + r.id() + "\"")
            .collect(Collectors.joining(", "));

        // Compact customer context — stats not raw documents
        String threatIntel = ctx.ipThreatInfo() != null
            ? ctx.ipThreatInfo().summary()
            : "IP " + event.sourceIp() + " not found in threat feeds (clean IP)";

        String recentAlerts = ctx.recentAlerts().isEmpty() ? "none" :
            ctx.recentAlerts().stream()
                .limit(5)
                .map(a -> a.ruleId() + "(" + a.severity() + ")")
                .collect(Collectors.joining(", "));

        String recentEvents = ctx.recentEvents().isEmpty() ? "none" :
            ctx.recentEvents().stream()
                .limit(5)
                .map(e -> String.format("%s/$%.0f/%s",
                    e.eventType(),
                    e.attributes() != null && e.attributes().get("amount") instanceof Number n
                        ? n.doubleValue() : 0.0,
                    e.sourceIp()))
                .collect(Collectors.joining(", "));

        return String.format("""
INSTRUCTION: Respond with ONLY a JSON array. No markdown. No explanation. Start with [ end with ].

You are a senior fraud analyst. Evaluate the current event against each rule below.
For each rule return one JSON object in the array.

CURRENT EVENT:
  id=%s | type=%s | customerId=%s | sourceIp=%s | device=%s
  amount=%s | merchant=%s | eventTime=%s

IP THREAT INTEL (real database lookup): %s

CUSTOMER 24H CONTEXT:
  events=%d | alerts=%d | maxAmount=$%.0f | avgAmount=$%.0f
  distinctIPs=%d | recentAlerts=[%s]
  recentEvents=[%s]

RULES TO EVALUATE:
%s
REQUIRED RESPONSE FORMAT — a JSON array with exactly %d objects, one per rule:
[
  {
    "ruleId": "RULE_ID_HERE",
    "fraud": true or false,
    "severity": "CRITICAL"|"HIGH"|"MEDIUM"|"LOW",
    "score": integer 0-100,
    "reason": "one sentence",
    "recommendation": "ALLOW"|"STEP_UP"|"HOLD"|"BLOCK"|"INVESTIGATE",
    "confidence": "HIGH"|"MEDIUM"|"LOW"
  }
]

Rules to include in order: [%s]
Respond ONLY with the JSON array starting with [ — nothing before or after.
""",
            event.id(), event.eventType(), event.customerId(),
            event.sourceIp(), event.deviceId(),
            event.attributes() != null ? event.attributes().getOrDefault("amount", "N/A") : "N/A",
            event.attributes() != null ? event.attributes().getOrDefault("merchant", "N/A") : "N/A",
            event.eventTime(),
            threatIntel,
            ctx.eventCount(), ctx.alertCount(),
            ctx.maxAmountToday(), ctx.avgAmountToday(),
            ctx.ipsSeen().size(),
            recentAlerts,
            recentEvents,
            ruleBlock,
            rules.size(),
            ruleIds
        );
    }

    // ── Inference call ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callInference(String prompt) {
        var response = inferenceClient.post()
            .uri("/_inference/completion/" + inferenceEndpointId)
            .header("Content-Type", "application/json")
            .body(Map.of("input", prompt))
            .retrieve()
            .body(Map.class);

        if (response == null || !response.containsKey("completion")) return null;

        var completion = (List<Map<String, Object>>) response.get("completion");
        if (completion == null || completion.isEmpty()) return null;

        return completion.get(0).get("result").toString().trim();
    }

    // ── Parse results ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResults(String raw,
                                                    List<RuleDefinition> rules) {
        // Strip markdown fences
        String cleaned = raw;
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```json\\s*", "")
                             .replaceAll("```\\s*", "").trim();
        }

        // Extract JSON array
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            log.warn("No JSON array in LLM response. Attempting to extract objects...");
            // Fall back: try to find individual JSON objects
            return extractObjectsAsList(cleaned, rules);
        }

        cleaned = cleaned.substring(start, end + 1);

        try {
            List<Object> parsed = objectMapper.readValue(cleaned, List.class);
            return parsed.stream()
                .filter(o -> o instanceof Map)
                .map(o -> (Map<String, Object>) o)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", e.getMessage());
            return extractObjectsAsList(cleaned, rules);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractObjectsAsList(String raw,
                                                            List<RuleDefinition> rules) {
        List<Map<String, Object>> results = new ArrayList<>();
        int pos = 0;
        while (pos < raw.length()) {
            int start = raw.indexOf('{', pos);
            if (start == -1) break;
            int end = findMatchingBrace(raw, start);
            if (end == -1) break;
            try {
                Map<String, Object> obj = objectMapper.readValue(
                    raw.substring(start, end + 1), Map.class);
                results.add(obj);
            } catch (Exception ignored) {}
            pos = end + 1;
        }
        return results;
    }

    private int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ── Build alerts ──────────────────────────────────────────────────────────

    private List<FraudAlert> buildAlerts(EventDocument event,
                                          List<RuleDefinition> rules,
                                          List<Map<String, Object>> results) {
        List<FraudAlert> alerts = new ArrayList<>();

        // Build a lookup from ruleId → result map
        Map<String, Map<String, Object>> byRuleId = new LinkedHashMap<>();
        for (Map<String, Object> r : results) {
            Object id = r.get("ruleId");
            if (id == null) id = r.get("rule_id");
            if (id != null) byRuleId.put(id.toString(), r);
        }

        // Match results to rules in order
        for (int i = 0; i < rules.size(); i++) {
            RuleDefinition rule = rules.get(i);

            // Try by ruleId match first, then by position
            Map<String, Object> result = byRuleId.get(rule.id());
            if (result == null && i < results.size()) {
                result = results.get(i);
            }
            if (result == null) {
                log.debug("No result for rule {}", rule.id());
                continue;
            }

            boolean fraud = resolveBoolean(result, "fraud",
                "rule_violation", "rule_violated", "violation_detected",
                "alert_triggered", "fraud_detected", "triggered");

            if (!fraud) {
                log.debug("[{}] No fraud detected for customer {}",
                    rule.id(), event.customerId());
                continue;
            }

            int score = resolveInt(result, "score",
                "fraud_score", "risk_score", "score_value");
            if (score == 0) score = rule.score(); // fall back to rule default

            String severity = resolveString(result, "severity", rule.severity());
            String reason = resolveString(result, "reason",
                "rationale", "reasoning", "explanation", "summary");
            String recommendation = resolveString(result, "recommendation",
                "action", "decision", "recommended_action");
            String confidence = resolveString(result, "confidence",
                "confidence_level", "detection_confidence");
            if (confidence == null) confidence = "MEDIUM";

            log.info("[{}] Fraud detected customer={} score={} confidence={} reason={}",
                rule.id(), event.customerId(), score, confidence,
                reason != null && reason.length() > 100
                    ? reason.substring(0, 100) + "..." : reason);

            alerts.add(new FraudAlert(
                UUID.randomUUID().toString(),
                event.tenantId(),
                event.id(),
                event.customerId(),
                rule.id(),
                severity,
                score,
                "[" + confidence + " confidence] " + reason
                    + " | Recommended action: " + recommendation,
                Instant.now()
            ));
        }

        return alerts;
    }

    // ── Field resolution helpers ──────────────────────────────────────────────

    private boolean resolveBoolean(Map<String, Object> m,
                                    String primary, String... aliases) {
        Object val = m.get(primary);
        if (val == null) {
            for (String a : aliases) {
                val = m.get(a);
                if (val != null) break;
            }
        }
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() > 0;
        if (val instanceof String s) {
            String l = s.toLowerCase().trim();
            return l.equals("true") || l.equals("yes") || l.equals("1");
        }
        return false;
    }

    private int resolveInt(Map<String, Object> m,
                            String primary, String... aliases) {
        Object val = m.get(primary);
        if (val == null) {
            for (String a : aliases) {
                val = m.get(a);
                if (val != null) break;
            }
        }
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private String resolveString(Map<String, Object> m,
                                  String primary, String... aliases) {
        Object val = m.get(primary);
        if (val == null) {
            for (String a : aliases) {
                val = m.get(a);
                if (val != null) break;
            }
        }
        if (val == null) return aliases.length > 0 ? aliases[aliases.length - 1] : null;
        if (val instanceof String s) return s;
        if (val instanceof Map || val instanceof List) return val.toString();
        return val.toString();
    }
}
