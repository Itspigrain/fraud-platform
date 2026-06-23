package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRequest;
import com.example.fraud.fraud.FraudEngine;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.rule.RuleEntity;
import com.example.fraud.rule.RuleService;
import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final FraudEngine fraudEngine;
    private final LogstashEventPublisher publisher;
    private final RuleService ruleService;

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

        List<RuleEntity> matchedRules = ruleService.evaluateEvent(contextTenant, doc);
        List<String> matchedRuleNames = matchedRules.stream()
            .map(RuleEntity::getName)
            .toList();

        log.info("fraud_evaluated customer={} score={} decision={} rulesFired={} userRulesMatched={}",
            doc.customerId(),
            result.audit().compositeRiskScore(),
            result.audit().decision(),
            result.audit().rulesFired(),
            matchedRuleNames);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", doc.id());
        response.put("alerts", result.alerts());
        response.put("riskScore", result.audit().compositeRiskScore());
        response.put("decision", result.audit().decision());
        response.put("matchedRules", matchedRuleNames);
        return response;
    }
}
