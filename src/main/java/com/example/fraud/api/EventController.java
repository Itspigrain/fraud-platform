package com.example.fraud.api;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRequest;
import com.example.fraud.alert.Alert;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.rule.RuleEntity;
import com.example.fraud.rule.RuleService;
import com.example.fraud.schema.EventSchemaEntity;
import com.example.fraud.schema.SchemaService;
import com.example.fraud.schema.SchemaValidationService;
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

    private final SchemaService schemaService;
    private final SchemaValidationService validationService;
    private final LogstashEventPublisher publisher;
    private final RuleService ruleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> ingest(@RequestBody EventRequest request) {
        String tenantId = TenantContext.getTenantId();

        if (request.eventType() == null || request.eventType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required");
        }

        EventSchemaEntity schema = schemaService.findSchema(tenantId, request.eventType())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No schema registered for event type: " + request.eventType()));

        var fields = schema.getParsedFields();
        var violations = validationService.validateAttributes(
            request.attributes() != null ? request.attributes() : Map.of(), fields);

        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Validation errors: " + violations);
        }

        Map<String, Object> cleanAttributes = validationService.stripUnknownFields(
            request.attributes() != null ? request.attributes() : Map.of(), fields);

        EventDocument doc = new EventDocument(
            UUID.randomUUID().toString(),
            tenantId,
            request.eventType(),
            request.eventTime() != null ? request.eventTime() : Instant.now(),
            cleanAttributes
        );

        publisher.writeEvent(doc);

        List<RuleEntity> matchedRules = ruleService.evaluateEvent(tenantId, request.eventType(), doc);

        List<Map<String, String>> verdicts = matchedRules.stream()
            .map(rule -> Map.of(
                "rule", rule.getName(),
                "verdict", rule.getVerdict() != null ? rule.getVerdict() : "REVIEW",
                "severity", rule.getSeverity() != null ? rule.getSeverity() : "HIGH",
                "reason", rule.getDescription() != null ? rule.getDescription() : ""
            ))
            .toList();

        for (RuleEntity rule : matchedRules) {
            Alert alert = new Alert(
                UUID.randomUUID().toString(),
                tenantId,
                doc.id(),
                rule.getName(),
                rule.getSeverity() != null ? rule.getSeverity() : "HIGH",
                rule.getVerdict() != null ? rule.getVerdict() : "REVIEW",
                rule.getDescription(),
                Instant.now()
            );
            publisher.writeAlert(alert);
        }

        List<String> matchedRuleNames = matchedRules.stream()
            .map(RuleEntity::getName)
            .toList();

        AuditEntry audit = new AuditEntry(
            UUID.randomUUID().toString(),
            tenantId,
            doc.id(),
            List.of(),
            matchedRuleNames,
            verdicts,
            Instant.now()
        );
        publisher.writeAudit(audit);

        log.info("event_ingested tenant={} eventType={} eventId={} verdicts={}",
            tenantId, request.eventType(), doc.id(), verdicts);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("eventId", doc.id());
        response.put("eventType", doc.eventType());
        response.put("verdicts", verdicts);
        return response;
    }
}
