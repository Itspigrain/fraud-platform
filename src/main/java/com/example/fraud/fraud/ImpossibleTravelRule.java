package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRepository;
import com.example.fraud.geo.GeoIpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ImpossibleTravelRule implements FraudRule {

    private static final Duration MAX_TRAVEL_TIME = Duration.ofHours(2);

    private final EventRepository eventRepository;
    private final GeoIpService geoIpService;

    @Override
    public String ruleId() {
        return "IMPOSSIBLE_TRAVEL";
    }

    @Override
    public Optional<FraudAlert> evaluate(EventDocument event) {
        Optional<EventDocument> previousEvent;
        try {
            previousEvent = eventRepository
                .findFirstByTenantIdAndCustomerIdOrderByEventTimeDesc(
                    event.tenantId(), event.customerId());
        } catch (Exception e) {
            return Optional.empty();
        }

        if (previousEvent.isEmpty()) return Optional.empty();

        var currentCountry = geoIpService.getCountry(event.sourceIp());
        if (currentCountry.isEmpty()) return Optional.empty();

        var previousCountry = geoIpService.getCountry(previousEvent.get().sourceIp());
        if (previousCountry.isEmpty()) return Optional.empty();

        if (currentCountry.get().equals(previousCountry.get())) return Optional.empty();

        Duration timeBetween = Duration.between(previousEvent.get().eventTime(), event.eventTime());
        if (timeBetween.compareTo(MAX_TRAVEL_TIME) >= 0) return Optional.empty();

        return Optional.of(new FraudAlert(
            UUID.randomUUID().toString(),
            event.tenantId(),
            event.id(),
            event.customerId(),
            "IMPOSSIBLE_TRAVEL",
            "CRITICAL",
            40,
            "Login from " + currentCountry.get() + " after " + previousCountry.get()
                + " within " + timeBetween.toMinutes() + " minutes",
            Instant.now()));
    }
}
