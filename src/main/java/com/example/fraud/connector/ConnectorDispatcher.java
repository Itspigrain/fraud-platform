package com.example.fraud.connector;

import com.example.fraud.alert.Alert;
import com.example.fraud.event.EventDocument;
import com.example.fraud.rule.RuleEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorDispatcher {

    private final ConnectorService connectorService;
    private final WebhookExecutor webhookExecutor;

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public void dispatch(String tenantId, EventDocument event,
                         List<RuleEntity> matchedRules, List<Alert> alerts) {
        List<ConnectorEntity> activeConnectors = connectorService.getActiveConnectors(tenantId);
        if (activeConnectors.isEmpty()) return;

        for (int i = 0; i < matchedRules.size(); i++) {
            RuleEntity rule = matchedRules.get(i);
            Alert alert = i < alerts.size() ? alerts.get(i) : null;

            for (ConnectorEntity connector : activeConnectors) {
                if (connector.getParsedRuleIds().contains(rule.getId())) {
                    try {
                        String payload = buildPayload(event, rule, alert, connector);
                        webhookExecutor.execute(connector, payload);
                    } catch (Exception e) {
                        log.error("Failed to dispatch connector={} for rule={}: {}",
                            connector.getId(), rule.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    private String buildPayload(EventDocument event, RuleEntity rule,
                                Alert alert, ConnectorEntity connector) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", event.id());
            payload.put("eventType", event.eventType());
            payload.put("tenantId", event.tenantId());
            payload.put("eventTime", event.eventTime().toString());
            payload.put("attributes", event.attributes());

            Map<String, Object> ruleMap = new LinkedHashMap<>();
            ruleMap.put("id", rule.getId());
            ruleMap.put("name", rule.getName());
            ruleMap.put("verdict", rule.getVerdict() != null ? rule.getVerdict() : "REVIEW");
            ruleMap.put("severity", rule.getSeverity() != null ? rule.getSeverity() : "HIGH");
            ruleMap.put("description", rule.getDescription() != null ? rule.getDescription() : "");
            payload.put("rule", ruleMap);

            if (alert != null) {
                Map<String, Object> alertMap = new LinkedHashMap<>();
                alertMap.put("alertId", alert.alertId());
                alertMap.put("detectedAt", alert.detectedAt().toString());
                payload.put("alert", alertMap);
            }

            Map<String, Object> connectorMap = new LinkedHashMap<>();
            connectorMap.put("id", connector.getId());
            connectorMap.put("name", connector.getName());
            payload.put("connector", connectorMap);

            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build webhook payload", e);
        }
    }
}
