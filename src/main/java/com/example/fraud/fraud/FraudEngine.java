package com.example.fraud.fraud;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FraudEngine {

    private final List<FraudRule> rules;
    private final RiskScoreCalculator riskScoreCalculator;

    public FraudEngine(List<FraudRule> rules, RiskScoreCalculator riskScoreCalculator) {
        this.rules = rules;
        this.riskScoreCalculator = riskScoreCalculator;
    }

    public EvaluationResult evaluate(EventDocument event) {
        List<String> rulesEvaluated = rules.stream()
            .map(FraudRule::ruleId)
            .toList();

        List<FraudAlert> alerts = rules.stream()
            .map(r -> r.evaluate(event))
            .flatMap(Optional::stream)
            .toList();

        List<String> rulesFired = alerts.stream()
            .map(FraudAlert::ruleId)
            .toList();

        int compositeScore = riskScoreCalculator.calculate(alerts);
        String decision = riskScoreCalculator.determineDecision(compositeScore);

        AuditEntry audit = new AuditEntry(
            UUID.randomUUID().toString(),
            event.id(),
            event.customerId(),
            rulesEvaluated,
            rulesFired,
            compositeScore,
            decision,
            Instant.now());

        return new EvaluationResult(alerts, audit);
    }
}
