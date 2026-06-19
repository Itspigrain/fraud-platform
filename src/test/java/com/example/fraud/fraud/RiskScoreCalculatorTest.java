package com.example.fraud.fraud;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoreCalculatorTest {

    private final RiskScoreCalculator calculator = new RiskScoreCalculator();

    @Test
    void calculateReturnsZeroForNoAlerts() {
        assertThat(calculator.calculate(List.of())).isEqualTo(0);
    }

    @Test
    void calculateSumsSingleAlertScore() {
        var alert = new FraudAlert("a1", "t1", "e1", "c1", "HIGH_VALUE", "HIGH", 30, "reason", Instant.now());
        assertThat(calculator.calculate(List.of(alert))).isEqualTo(30);
    }

    @Test
    void calculateSumsMultipleAlertScores() {
        var a1 = new FraudAlert("a1", "t1", "e1", "c1", "HIGH_VALUE", "HIGH", 30, "r1", Instant.now());
        var a2 = new FraudAlert("a2", "t1", "e1", "c1", "VELOCITY", "MEDIUM", 30, "r2", Instant.now());
        assertThat(calculator.calculate(List.of(a1, a2))).isEqualTo(60);
    }

    @Test
    void calculateCapsAt100() {
        var a1 = new FraudAlert("a1", "t1", "e1", "c1", "HIGH_VALUE", "HIGH", 30, "r1", Instant.now());
        var a2 = new FraudAlert("a2", "t1", "e1", "c1", "IMP_TRAVEL", "CRITICAL", 40, "r2", Instant.now());
        var a3 = new FraudAlert("a3", "t1", "e1", "c1", "SUSPICIOUS_IP", "HIGH", 50, "r3", Instant.now());
        assertThat(calculator.calculate(List.of(a1, a2, a3))).isEqualTo(100);
    }

    @Test
    void determineDecisionAllow() {
        assertThat(calculator.determineDecision(0)).isEqualTo("ALLOW");
        assertThat(calculator.determineDecision(30)).isEqualTo("ALLOW");
    }

    @Test
    void determineDecisionFlag() {
        assertThat(calculator.determineDecision(31)).isEqualTo("FLAG");
        assertThat(calculator.determineDecision(60)).isEqualTo("FLAG");
    }

    @Test
    void determineDecisionBlock() {
        assertThat(calculator.determineDecision(61)).isEqualTo("BLOCK");
        assertThat(calculator.determineDecision(100)).isEqualTo("BLOCK");
    }
}
