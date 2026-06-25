package com.example.fraud.ai;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.EvaluationResult;
import com.example.fraud.fraud.FraudAlert;
import com.example.fraud.fraud.RiskScoreCalculator;
import com.example.fraud.risk.MemberRiskFlag;
import com.example.fraud.risk.RiskFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI-powered fraud engine — Priority 4 + Priority 1 combined:
 *
 * STEP 1: Check persistent risk flag (ECH _doc GET — microseconds, zero tokens)
 *   → Flag exists and BLOCK/HOLD? Return immediately. No LLM call.
 *   → Flag exists and INVESTIGATE/STEP_UP? Enrich context but still call LLM.
 *   → No flag? Proceed to LLM evaluation.
 *
 * STEP 2: Single LLM call evaluates ALL rules (Priority 1)
 *   → ~2,500 tokens instead of ~11,600 (8 separate calls)
 *   → ~8-12s instead of ~80s
 *
 * STEP 3: Write persistent flag if score >= threshold (Priority 4)
 *   → Future events from same customer skip LLM entirely
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "fraud.ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AiFraudEngine {

    private final AiRuleConfigService ruleConfigService;
    private final CustomerContextService contextService;
    private final RiskScoreCalculator riskScoreCalculator;
    private final RiskFlagService riskFlagService;
    private final RestClient inferenceClient;
    private final ObjectMapper objectMapper;

    @Value("${fraud.ai.inference-endpoint-id:.anthropic-claude-4.5-haiku-completion}")
    private String inferenceEndpointId;

    @Value("${fraud.risk-flag.skip-llm-on-block:true}")
    private boolean skipLlmOnBlock;

    public EvaluationResult evaluate(EventDocument event) {
        List<RuleDefinition> rules = ruleConfigService.getRules();

        // ── Step 1: Check persistent risk flag ───────────────────────────────
        Optional<MemberRiskFlag> existingFlag =
            riskFlagService.getActiveFlag(event.tenantId(), event.customerId());

        if (existingFlag.isPresent() && skipLlmOnBlock) {
            MemberRiskFlag flag = existingFlag.get();
            String rec = flag.recommendation() != null ? flag.recommendation() : "BLOCK";

            // For BLOCK-level flags, skip LLM entirely and return immediately
            if ("BLOCK".equals(rec) || ("HOLD".equals(rec) && flag.riskScore() >= 80)) {
                log.info("Risk flag shortcut: customer={} level={} score={} — skipping LLM",
                    event.customerId(), flag.riskLevel(), flag.riskScore());

                FraudAlert flagAlert = new FraudAlert(
                    UUID.randomUUID().toString(),
                    event.tenantId(), event.id(), event.customerId(),
                    "RISK_FLAG",
                    flag.riskLevel(),
                    flag.riskScore(),
                    "[PERSISTENT FLAG] Customer has active risk flag from " +
                    flag.flaggedAt() + ". Original: " + flag.primaryReason() +
                    " | Rules: " + flag.rulesFired() +
                    " | Recommended action: " + rec,
                    Instant.now()
                );

                String decision = riskScoreCalculator.determineDecision(flag.riskScore());
                return buildResult(event,
                    rules.stream().map(RuleDefinition::id).toList(),
                    List.of(flagAlert),
                    flag.riskScore(),
                    decision);
            }
        }

        if (rules.isEmpty()) {
            log.warn("No AI rules configured — returning ALLOW for event {}", event.id());
            return buildResult(event, List.of(), List.of(), 0, "ALLOW");
        }

        log.info("AI engine evaluating event={} customer={} rules={} existingFlag={}",
            event.id(), event.customerId(),
            rules.stream().map(RuleDefinition::id).toList(),
            existingFlag.map(f -> f.riskLevel() + "/" + f.riskScore()).orElse("none"));

        // ── Step 2: Fetch 24h context (single ES query) ───────────────────────
        CustomerContext context = contextService.buildContext(
            event.tenantId(), event.customerId(), event.sourceIp());

        // ── Step 3: Single LLM call — all rules at once ───────────────────────
        MultiRuleEvaluator evaluator = new MultiRuleEvaluator(
            inferenceClient, objectMapper, inferenceEndpointId);

        List<FraudAlert> alerts = evaluator.evaluateAll(event, context, rules);

        List<String> rulesEvaluated = rules.stream().map(RuleDefinition::id).toList();
        List<String> rulesFired = alerts.stream().map(FraudAlert::ruleId).toList();
        int score = riskScoreCalculator.calculate(alerts);
        String decision = riskScoreCalculator.determineDecision(score);

        log.info("AI evaluation complete: event={} customer={} fired={} score={} decision={}",
            event.id(), event.customerId(), rulesFired, score, decision);

        // ── Step 4: Write persistent risk flag if threshold exceeded ──────────
        if (!alerts.isEmpty() && (score >= 61 || "FLAG".equals(decision))) {
            riskFlagService.createOrUpdateFlag(
                event.tenantId(), event.customerId(),
                event.id(), alerts, score, decision);
        }

        return buildResult(event, rulesEvaluated, alerts, score, decision);
    }

    private EvaluationResult buildResult(EventDocument event,
                                          List<String> rulesEvaluated,
                                          List<FraudAlert> alerts,
                                          int score, String decision) {
        List<String> rulesFired = alerts.stream().map(FraudAlert::ruleId).toList();
        AuditEntry audit = new AuditEntry(
            UUID.randomUUID().toString(),
            event.tenantId(), event.id(), event.customerId(),
            rulesEvaluated, rulesFired, score, decision, Instant.now());
        return new EvaluationResult(alerts, audit);
    }
}
