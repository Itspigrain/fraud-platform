package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.geo.GeoIpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspiciousIpRuleTest {

    @Mock
    private GeoIpService geoIpService;

    @Test
    void ruleIdReturnsSuspiciousIp() {
        var rule = new SuspiciousIpRule(geoIpService, List.of(), List.of());
        assertThat(rule.ruleId()).isEqualTo("SUSPICIOUS_IP");
    }

    @Test
    void firesWhenIpOnBlocklist() {
        var rule = new SuspiciousIpRule(geoIpService, List.of("10.0.0.1"), List.of());
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "10.0.0.1",
            null, null, null, Instant.now(), Map.of(), 0);

        var result = rule.evaluate(event);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("SUSPICIOUS_IP");
        assertThat(result.get().severity()).isEqualTo("HIGH");
        assertThat(result.get().riskScore()).isEqualTo(50);
    }

    @Test
    void firesWhenCountryIsHighRisk() {
        var rule = new SuspiciousIpRule(geoIpService, List.of(), List.of("North Korea"));
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "5.6.7.8",
            null, null, null, Instant.now(), Map.of(), 0);

        when(geoIpService.getCountry("5.6.7.8")).thenReturn(Optional.of("North Korea"));

        var result = rule.evaluate(event);

        assertThat(result).isPresent();
        assertThat(result.get().reason()).contains("high-risk country");
    }

    @Test
    void doesNotFireWhenIpCleanAndCountrySafe() {
        var rule = new SuspiciousIpRule(geoIpService, List.of("10.0.0.1"), List.of("North Korea"));
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        when(geoIpService.getCountry("1.2.3.4")).thenReturn(Optional.of("Australia"));

        assertThat(rule.evaluate(event)).isEmpty();
    }

    @Test
    void doesNotFireWhenGeoIpReturnsEmpty() {
        var rule = new SuspiciousIpRule(geoIpService, List.of(), List.of("North Korea"));
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        when(geoIpService.getCountry("1.2.3.4")).thenReturn(Optional.empty());

        assertThat(rule.evaluate(event)).isEmpty();
    }
}
