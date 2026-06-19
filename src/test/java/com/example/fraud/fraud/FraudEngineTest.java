package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FraudEngineTest {

    @Test
    void evaluateReturnsEvaluationResultWithAlertsAndAudit() {
        FraudRule firingRule = new FraudRule() {
            @Override public String ruleId() { return "TEST_RULE"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.of(new FraudAlert(
                    UUID.randomUUID().toString(), event.tenantId(), event.id(), event.customerId(),
                    "TEST_RULE", "HIGH", 30, "test", Instant.now()));
            }
        };

        FraudRule passingRule = new FraudRule() {
            @Override public String ruleId() { return "PASS_RULE"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.empty();
            }
        };

        var calculator = new RiskScoreCalculator();
        var engine = new FraudEngine(List.of(firingRule, passingRule), calculator);

        var event = new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of("amount", 15000), 0);

        var result = engine.evaluate(event);

        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().get(0).ruleId()).isEqualTo("TEST_RULE");

        assertThat(result.audit().eventId()).isEqualTo("e1");
        assertThat(result.audit().customerId()).isEqualTo("c1");
        assertThat(result.audit().rulesEvaluated()).containsExactly("TEST_RULE", "PASS_RULE");
        assertThat(result.audit().rulesFired()).containsExactly("TEST_RULE");
        assertThat(result.audit().compositeRiskScore()).isEqualTo(30);
        assertThat(result.audit().decision()).isEqualTo("ALLOW");
    }

    @Test
    void evaluateReturnsBlockDecisionForHighScore() {
        FraudRule rule1 = new FraudRule() {
            @Override public String ruleId() { return "R1"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.of(new FraudAlert(
                    UUID.randomUUID().toString(), event.tenantId(), event.id(), event.customerId(),
                    "R1", "HIGH", 40, "r1", Instant.now()));
            }
        };

        FraudRule rule2 = new FraudRule() {
            @Override public String ruleId() { return "R2"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.of(new FraudAlert(
                    UUID.randomUUID().toString(), event.tenantId(), event.id(), event.customerId(),
                    "R2", "HIGH", 50, "r2", Instant.now()));
            }
        };

        var calculator = new RiskScoreCalculator();
        var engine = new FraudEngine(List.of(rule1, rule2), calculator);

        var event = new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        var result = engine.evaluate(event);

        assertThat(result.audit().compositeRiskScore()).isEqualTo(90);
        assertThat(result.audit().decision()).isEqualTo("BLOCK");
    }

    @Test
    void evaluatePropagateTenantIdToAudit() {
        FraudRule passingRule = new FraudRule() {
            @Override public String ruleId() { return "PASS"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.empty();
            }
        };

        var calculator = new RiskScoreCalculator();
        var engine = new FraudEngine(List.of(passingRule), calculator);

        var event = new EventDocument("e1", "tenant-xyz", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        var result = engine.evaluate(event);

        assertThat(result.audit().tenantId()).isEqualTo("tenant-xyz");
    }

    @Test
    void evaluateWithNoFiringRulesReturnsAllowDecision() {
        FraudRule passingRule = new FraudRule() {
            @Override public String ruleId() { return "PASS"; }
            @Override public Optional<FraudAlert> evaluate(EventDocument event) {
                return Optional.empty();
            }
        };

        var calculator = new RiskScoreCalculator();
        var engine = new FraudEngine(List.of(passingRule), calculator);

        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        var result = engine.evaluate(event);

        assertThat(result.alerts()).isEmpty();
        assertThat(result.audit().compositeRiskScore()).isEqualTo(0);
        assertThat(result.audit().decision()).isEqualTo("ALLOW");
        assertThat(result.audit().rulesFired()).isEmpty();
    }
}
