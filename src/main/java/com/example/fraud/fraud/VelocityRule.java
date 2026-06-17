package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class VelocityRule implements FraudRule {

    private final EventRepository eventRepository;

    public VelocityRule(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public String ruleId() {
        return "VELOCITY";
    }

    @Override
    public Optional<FraudAlert> evaluate(EventDocument event) {
        List<EventDocument> recentEvents;
        try {
            Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
            recentEvents = eventRepository.findByCustomerIdAndEventTimeAfter(
                event.customerId(), tenMinutesAgo);
        } catch (Exception e) {
            return Optional.empty();
        }

        if (recentEvents.size() > 5) {
            return Optional.of(new FraudAlert(
                UUID.randomUUID().toString(),
                event.id(),
                event.customerId(),
                "VELOCITY",
                "MEDIUM",
                30,
                "More than 5 transactions in 10 minutes",
                Instant.now()));
        }
        return Optional.empty();
    }
}
