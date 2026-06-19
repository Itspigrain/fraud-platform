package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class HighValueTransactionRule implements FraudRule {

    @Override
    public String ruleId() {
        return "HIGH_VALUE";
    }

    @Override
    public Optional<FraudAlert> evaluate(EventDocument event) {
        Object amount = event.attributes() != null ? event.attributes().get("amount") : null;
        if (amount instanceof Number n && n.doubleValue() > 10_000) {
            return Optional.of(new FraudAlert(
                UUID.randomUUID().toString(),
                event.tenantId(),
                event.id(),
                event.customerId(),
                "HIGH_VALUE",
                "HIGH",
                30,
                "Transaction exceeds threshold",
                Instant.now()));
        }
        return Optional.empty();
    }
}
