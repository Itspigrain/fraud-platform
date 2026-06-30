package com.example.fraud.connector;

import com.example.fraud.alert.Alert;
import com.example.fraud.event.EventDocument;
import com.example.fraud.rule.RuleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorDispatcherTest {

    @Mock private ConnectorService connectorService;
    @Mock private WebhookExecutor webhookExecutor;

    private ConnectorDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ConnectorDispatcher(connectorService, webhookExecutor);
    }

    @Test
    void dispatchesWebhookForMatchingRule() {
        RuleEntity rule = new RuleEntity();
        rule.setId(5L);
        rule.setName("test-rule");
        rule.setVerdict("BLOCK");
        rule.setSeverity("CRITICAL");

        ConnectorEntity connector = new ConnectorEntity();
        connector.setId(10L);
        connector.setName("slack-hook");
        connector.setRuleIdsFromList(List.of(5L));
        connector.setConfigFromMap(Map.of("url", "https://example.com/hook"));
        connector.setRetryAttempts(3);
        connector.setRetryDelayMs(1000);

        EventDocument event = new EventDocument("e1", "t1", "purchase", Instant.now(), Map.of("amount", 9999), null);
        Alert alert = new Alert("a1", "t1", "e1", "test-rule", "CRITICAL", "BLOCK", "desc", Instant.now());

        when(connectorService.getActiveConnectors("t1")).thenReturn(List.of(connector));

        dispatcher.dispatch("t1", event, List.of(rule), List.of(alert));

        verify(webhookExecutor).execute(eq(connector), any(String.class));
    }

    @Test
    void skipsConnectorWhenRuleNotBound() {
        RuleEntity rule = new RuleEntity();
        rule.setId(5L);
        rule.setName("test-rule");

        ConnectorEntity connector = new ConnectorEntity();
        connector.setId(10L);
        connector.setName("other-hook");
        connector.setRuleIdsFromList(List.of(99L));

        EventDocument event = new EventDocument("e1", "t1", "purchase", Instant.now(), Map.of(), null);
        Alert alert = new Alert("a1", "t1", "e1", "test-rule", "HIGH", "REVIEW", "desc", Instant.now());

        when(connectorService.getActiveConnectors("t1")).thenReturn(List.of(connector));

        dispatcher.dispatch("t1", event, List.of(rule), List.of(alert));

        verifyNoInteractions(webhookExecutor);
    }

    @Test
    void dispatchesMultipleConnectorsForSameRule() {
        RuleEntity rule = new RuleEntity();
        rule.setId(5L);
        rule.setName("test-rule");

        ConnectorEntity c1 = new ConnectorEntity();
        c1.setId(10L);
        c1.setName("hook-1");
        c1.setRuleIdsFromList(List.of(5L));
        c1.setConfigFromMap(Map.of("url", "https://example.com/hook1"));
        c1.setRetryAttempts(3);
        c1.setRetryDelayMs(1000);

        ConnectorEntity c2 = new ConnectorEntity();
        c2.setId(11L);
        c2.setName("hook-2");
        c2.setRuleIdsFromList(List.of(5L));
        c2.setConfigFromMap(Map.of("url", "https://example.com/hook2"));
        c2.setRetryAttempts(3);
        c2.setRetryDelayMs(1000);

        EventDocument event = new EventDocument("e1", "t1", "purchase", Instant.now(), Map.of(), null);
        Alert alert = new Alert("a1", "t1", "e1", "test-rule", "HIGH", "REVIEW", "desc", Instant.now());

        when(connectorService.getActiveConnectors("t1")).thenReturn(List.of(c1, c2));

        dispatcher.dispatch("t1", event, List.of(rule), List.of(alert));

        verify(webhookExecutor).execute(eq(c1), any(String.class));
        verify(webhookExecutor).execute(eq(c2), any(String.class));
    }

    @Test
    void webhookPayloadIncludesExportedFeatures() throws Exception {
        RuleEntity rule = new RuleEntity();
        rule.setId(5L);
        rule.setName("test-rule");
        rule.setVerdict("BLOCK");
        rule.setSeverity("CRITICAL");

        ConnectorEntity connector = new ConnectorEntity();
        connector.setId(10L);
        connector.setName("slack-hook");
        connector.setRuleIdsFromList(List.of(5L));
        connector.setConfigFromMap(Map.of("url", "https://example.com/hook"));
        connector.setRetryAttempts(3);
        connector.setRetryDelayMs(1000);

        Map<String, Object> features = Map.of("test_rule_matched", true, "test_rule_count", 12L);
        EventDocument event = new EventDocument("e1", "t1", "purchase", Instant.now(),
            Map.of("amount", 9999), features);
        Alert alert = new Alert("a1", "t1", "e1", "test-rule", "CRITICAL", "BLOCK", "desc", Instant.now());

        when(connectorService.getActiveConnectors("t1")).thenReturn(List.of(connector));

        dispatcher.dispatch("t1", event, List.of(rule), List.of(alert));

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(webhookExecutor).execute(eq(connector), captor.capture());

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = mapper.readTree(captor.getValue());
        assertThat(payload.has("exportedFeatures")).isTrue();
        assertThat(payload.get("exportedFeatures").get("test_rule_matched").asBoolean()).isTrue();
        assertThat(payload.get("exportedFeatures").get("test_rule_count").asLong()).isEqualTo(12L);
    }

    @Test
    void noOpWhenNoActiveConnectors() {
        RuleEntity rule = new RuleEntity();
        rule.setId(5L);

        EventDocument event = new EventDocument("e1", "t1", "purchase", Instant.now(), Map.of(), null);

        when(connectorService.getActiveConnectors("t1")).thenReturn(List.of());

        dispatcher.dispatch("t1", event, List.of(rule), List.of());

        verifyNoInteractions(webhookExecutor);
    }
}
