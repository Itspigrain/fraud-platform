package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HighValueTransactionRuleTest {

    private final HighValueTransactionRule rule = new HighValueTransactionRule();

    @Test
    void ruleIdReturnsHighValue() {
        assertThat(rule.ruleId()).isEqualTo("HIGH_VALUE");
    }

    @Test
    void firesWhenAmountExceedsThreshold() {
        var event = new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of("amount", 15000), 0);

        var result = rule.evaluate(event);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("HIGH_VALUE");
        assertThat(result.get().severity()).isEqualTo("HIGH");
        assertThat(result.get().riskScore()).isEqualTo(30);
        assertThat(result.get().eventId()).isEqualTo("e1");
        assertThat(result.get().customerId()).isEqualTo("c1");
    }

    @Test
    void doesNotFireWhenAmountBelowThreshold() {
        var event = new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of("amount", 5000), 0);

        assertThat(rule.evaluate(event)).isEmpty();
    }

    @Test
    void doesNotFireWhenNoAmount() {
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        assertThat(rule.evaluate(event)).isEmpty();
    }

    @Test
    void doesNotFireWhenNullAttributes() {
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), null, 0);

        assertThat(rule.evaluate(event)).isEmpty();
    }
}
