package com.example.fraud.fraud;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RiskScoreCalculator {

    public int calculate(List<FraudAlert> alerts) {
        int sum = alerts.stream().mapToInt(FraudAlert::riskScore).sum();
        return Math.min(sum, 100);
    }

    public String determineDecision(int score) {
        if (score <= 30) return "ALLOW";
        if (score <= 60) return "FLAG";
        return "BLOCK";
    }
}
