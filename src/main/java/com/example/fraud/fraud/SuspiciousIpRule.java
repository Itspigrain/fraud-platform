package com.example.fraud.fraud;

import com.example.fraud.event.EventDocument;
import com.example.fraud.geo.GeoIpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SuspiciousIpRule implements FraudRule {

    private final GeoIpService geoIpService;
    private final List<String> blockedIps;
    private final List<String> highRiskCountries;

    public SuspiciousIpRule(
            GeoIpService geoIpService,
            @Value("${fraud.rules.suspicious-ip.blocked-ips:}") List<String> blockedIps,
            @Value("${fraud.rules.suspicious-ip.high-risk-countries:}") List<String> highRiskCountries) {
        this.geoIpService = geoIpService;
        this.blockedIps = blockedIps;
        this.highRiskCountries = highRiskCountries;
    }

    @Override
    public String ruleId() {
        return "SUSPICIOUS_IP";
    }

    @Override
    public Optional<FraudAlert> evaluate(EventDocument event) {
        if (blockedIps.contains(event.sourceIp())) {
            return buildAlert(event, "Source IP is on blocklist");
        }

        Optional<String> country = geoIpService.getCountry(event.sourceIp());
        if (country.isPresent() && highRiskCountries.contains(country.get())) {
            return buildAlert(event, "Source IP from high-risk country: " + country.get());
        }

        return Optional.empty();
    }

    private Optional<FraudAlert> buildAlert(EventDocument event, String reason) {
        return Optional.of(new FraudAlert(
            UUID.randomUUID().toString(),
            event.id(),
            event.customerId(),
            "SUSPICIOUS_IP",
            "HIGH",
            50,
            reason,
            Instant.now()));
    }
}
