package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRepository;
import com.example.fraud.geo.GeoIpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpossibleTravelRuleTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private GeoIpService geoIpService;

    @InjectMocks
    private ImpossibleTravelRule rule;

    @Test
    void ruleIdReturnsImpossibleTravel() {
        assertThat(rule.ruleId()).isEqualTo("IMPOSSIBLE_TRAVEL");
    }

    @Test
    void firesWhenDifferentCountryWithinOneHour() {
        Instant now = Instant.now();
        Instant thirtyMinutesAgo = now.minus(30, ChronoUnit.MINUTES);

        var currentEvent = new EventDocument("e2", "t1", "LOGIN", "c1", "5.6.7.8",
            null, null, null, now, Map.of(), 0);
        var previousEvent = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, thirtyMinutesAgo, Map.of(), 0);

        when(eventRepository.findFirstByCustomerIdOrderByEventTimeDesc("c1"))
            .thenReturn(Optional.of(previousEvent));
        when(geoIpService.getCountry("5.6.7.8")).thenReturn(Optional.of("Russia"));
        when(geoIpService.getCountry("1.2.3.4")).thenReturn(Optional.of("Australia"));

        var result = rule.evaluate(currentEvent);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("IMPOSSIBLE_TRAVEL");
        assertThat(result.get().severity()).isEqualTo("CRITICAL");
        assertThat(result.get().riskScore()).isEqualTo(40);
    }

    @Test
    void doesNotFireWhenSameCountry() {
        Instant now = Instant.now();
        var currentEvent = new EventDocument("e2", "t1", "LOGIN", "c1", "5.6.7.8",
            null, null, null, now, Map.of(), 0);
        var previousEvent = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, now.minus(10, ChronoUnit.MINUTES), Map.of(), 0);

        when(eventRepository.findFirstByCustomerIdOrderByEventTimeDesc("c1"))
            .thenReturn(Optional.of(previousEvent));
        when(geoIpService.getCountry("5.6.7.8")).thenReturn(Optional.of("Australia"));
        when(geoIpService.getCountry("1.2.3.4")).thenReturn(Optional.of("Australia"));

        assertThat(rule.evaluate(currentEvent)).isEmpty();
    }

    @Test
    void doesNotFireWhenNoPreviousEvent() {
        var event = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);

        when(eventRepository.findFirstByCustomerIdOrderByEventTimeDesc("c1"))
            .thenReturn(Optional.empty());

        assertThat(rule.evaluate(event)).isEmpty();
    }

    @Test
    void doesNotFireWhenDifferentCountryButEnoughTimePassed() {
        Instant now = Instant.now();
        Instant fiveHoursAgo = now.minus(5, ChronoUnit.HOURS);

        var currentEvent = new EventDocument("e2", "t1", "LOGIN", "c1", "5.6.7.8",
            null, null, null, now, Map.of(), 0);
        var previousEvent = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, fiveHoursAgo, Map.of(), 0);

        when(eventRepository.findFirstByCustomerIdOrderByEventTimeDesc("c1"))
            .thenReturn(Optional.of(previousEvent));
        when(geoIpService.getCountry("5.6.7.8")).thenReturn(Optional.of("Russia"));
        when(geoIpService.getCountry("1.2.3.4")).thenReturn(Optional.of("Australia"));

        assertThat(rule.evaluate(currentEvent)).isEmpty();
    }

    @Test
    void doesNotFireWhenGeoIpLookupFails() {
        var currentEvent = new EventDocument("e2", "t1", "LOGIN", "c1", "5.6.7.8",
            null, null, null, Instant.now(), Map.of(), 0);
        var previousEvent = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now().minus(10, ChronoUnit.MINUTES), Map.of(), 0);

        when(eventRepository.findFirstByCustomerIdOrderByEventTimeDesc("c1"))
            .thenReturn(Optional.of(previousEvent));
        when(geoIpService.getCountry("5.6.7.8")).thenReturn(Optional.empty());

        assertThat(rule.evaluate(currentEvent)).isEmpty();
    }
}
