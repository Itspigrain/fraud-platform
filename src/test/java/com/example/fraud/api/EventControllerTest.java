package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRequest;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.rule.RuleService;
import com.example.fraud.schema.*;
import com.example.fraud.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock private SchemaService schemaService;
    @Mock private SchemaValidationService validationService;
    @Mock private LogstashEventPublisher publisher;
    @Mock private RuleService ruleService;

    private EventController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("t1");
        controller = new EventController(schemaService, validationService, publisher, ruleService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ingestValidatesAndPublishesEvent() {
        var schema = new EventSchemaEntity();
        schema.setTenantId("t1");
        schema.setEventType("purchase");
        schema.setFieldsFromList(List.of(
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null)
        ));

        when(schemaService.findSchema("t1", "purchase")).thenReturn(Optional.of(schema));
        when(validationService.validateAttributes(anyMap(), anyList())).thenReturn(List.of());
        when(validationService.stripUnknownFields(anyMap(), anyList())).thenReturn(Map.of("amount", 99.0));
        when(ruleService.evaluateEvent(eq("t1"), eq("purchase"), any())).thenReturn(List.of());

        var request = new EventRequest("purchase", Instant.now(), Map.of("amount", 99.0));
        var response = controller.ingest(request);

        assertThat(response.get("eventId")).isNotNull();
        assertThat(response.get("eventType")).isEqualTo("purchase");
        assertThat(response.get("decision")).isEqualTo("ALLOW");
        verify(publisher).writeEvent(any(EventDocument.class));
        verify(publisher).writeAudit(any());
    }

    @Test
    void ingestRejectsMissingSchema() {
        when(schemaService.findSchema("t1", "unknown")).thenReturn(Optional.empty());

        var request = new EventRequest("unknown", Instant.now(), Map.of());

        assertThatThrownBy(() -> controller.ingest(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No schema registered");
    }

    @Test
    void ingestRejectsValidationFailures() {
        var schema = new EventSchemaEntity();
        schema.setTenantId("t1");
        schema.setEventType("purchase");
        schema.setFieldsFromList(List.of(
            new SchemaFieldDefinition("amount", SchemaFieldType.DOUBLE, true, null)
        ));

        when(schemaService.findSchema("t1", "purchase")).thenReturn(Optional.of(schema));
        when(validationService.validateAttributes(anyMap(), anyList()))
            .thenReturn(List.of(new SchemaValidationService.Violation("amount", "required")));

        var request = new EventRequest("purchase", Instant.now(), Map.of());

        assertThatThrownBy(() -> controller.ingest(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Validation errors");
    }

    @Test
    void ingestRejectsMissingEventType() {
        var request = new EventRequest(null, Instant.now(), Map.of());

        assertThatThrownBy(() -> controller.ingest(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("eventType is required");
    }
}
