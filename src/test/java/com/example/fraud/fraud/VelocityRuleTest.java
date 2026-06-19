package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private VelocityRule rule;

    @Test
    void ruleIdReturnsVelocity() {
        assertThat(rule.ruleId()).isEqualTo("VELOCITY");
    }

    @Test
    void firesWhenMoreThanFiveRecentEvents() {
        var event = new EventDocument("e6", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        List<EventDocument> recentEvents = List.of(
            new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e2", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e3", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e4", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e5", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e7", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0)
        );

        when(eventRepository.findByTenantIdAndCustomerIdAndEventTimeAfter(
            eq("t1"), eq("c1"), any(Instant.class)))
            .thenReturn(recentEvents);

        var result = rule.evaluate(event);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("VELOCITY");
        assertThat(result.get().severity()).isEqualTo("MEDIUM");
        assertThat(result.get().riskScore()).isEqualTo(30);
    }

    @Test
    void doesNotFireWhenFiveOrFewerRecentEvents() {
        var event = new EventDocument("e3", "t1", "PAYMENT", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        List<EventDocument> recentEvents = List.of(
            new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0),
            new EventDocument("e2", "t1", "PAYMENT", "c1", "1.2.3.4", null, null, null, Instant.now(), Map.of(), 0)
        );

        when(eventRepository.findByTenantIdAndCustomerIdAndEventTimeAfter(
            eq("t1"), eq("c1"), any(Instant.class)))
            .thenReturn(recentEvents);

        assertThat(rule.evaluate(event)).isEmpty();
    }

    @Test
    void doesNotFireWhenNoRecentEvents() {
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        when(eventRepository.findByTenantIdAndCustomerIdAndEventTimeAfter(
            eq("t1"), eq("c1"), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        assertThat(rule.evaluate(event)).isEmpty();
    }
}
