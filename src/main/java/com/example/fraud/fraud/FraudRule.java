package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import java.util.Optional;

public interface FraudRule {
    String ruleId();
    Optional<FraudAlert> evaluate(EventDocument event);
}
