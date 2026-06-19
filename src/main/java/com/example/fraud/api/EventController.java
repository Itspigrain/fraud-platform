package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRequest;
import com.example.fraud.fraud.FraudEngine;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final FraudEngine fraudEngine;
    private final LogstashEventPublisher publisher;

    public EventController(FraudEngine fraudEngine, LogstashEventPublisher publisher) {
        this.fraudEngine = fraudEngine;
        this.publisher = publisher;
    }

    @PostMapping
    public Map<String, Object> ingest(@RequestBody EventRequest request) {
        String contextTenant = TenantContext.getTenantId();
        if (!contextTenant.equals(request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Tenant mismatch: header=" + contextTenant + " body=" + request.tenantId());
        }

        EventDocument doc = new EventDocument(
            UUID.randomUUID().toString(),
            request.tenantId(),
            request.eventType(),
            request.customerId(),
            request.sourceIp(),
            request.deviceId(),
            request.email(),
            request.phoneNumber(),
            request.eventTime() == null ? Instant.now() : request.eventTime(),
            request.attributes(),
            0
        );

        var result = fraudEngine.evaluate(doc);
        doc = doc.withRiskScore(result.audit().compositeRiskScore());

        publisher.writeEvent(doc);
        result.alerts().forEach(publisher::writeAlert);
        publisher.writeAudit(result.audit());

        log.info("fraud_evaluated customer={} score={} decision={} rulesFired={}",
            doc.customerId(),
            result.audit().compositeRiskScore(),
            result.audit().decision(),
            result.audit().rulesFired());

        return Map.of(
            "eventId", doc.id(),
            "alerts", result.alerts(),
            "riskScore", result.audit().compositeRiskScore(),
            "decision", result.audit().decision()
        );
    }
}
