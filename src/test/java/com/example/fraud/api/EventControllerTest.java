package com.example.fraud.api;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventRequest;
import com.example.fraud.fraud.*;
import com.example.fraud.pipeline.LogstashEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventControllerTest {

    @Test
    void ingestBuildsEventEvaluatesAndPublishes() {
        var fraudEngine = mock(FraudEngine.class);
        var publisher = mock(LogstashEventPublisher.class);

        var alert = new FraudAlert("a1", "ignored", "c1", "HIGH_VALUE", "HIGH",
            30, "reason", Instant.now());
        var audit = new AuditEntry("au1", "ignored", "c1",
            List.of("HIGH_VALUE"), List.of("HIGH_VALUE"), 30, "ALLOW", Instant.now());
        var result = new EvaluationResult(List.of(alert), audit);

        when(fraudEngine.evaluate(any(EventDocument.class))).thenReturn(result);

        var controller = new EventController(fraudEngine, publisher);
        var request = new EventRequest("t1", "PAYMENT", "c1", "1.2.3.4",
            "d1", "a@b.com", "555", Instant.parse("2026-06-16T12:00:00Z"),
            Map.of("amount", 15000));

        var response = controller.ingest(request);

        assertThat(response.get("eventId")).isNotNull();
        assertThat(response.get("riskScore")).isEqualTo(30);
        assertThat(response.get("decision")).isEqualTo("ALLOW");

        @SuppressWarnings("unchecked")
        var alerts = (List<FraudAlert>) response.get("alerts");
        assertThat(alerts).hasSize(1);

        verify(publisher).writeEvent(any(EventDocument.class));
        verify(publisher).writeAlert(alert);
        verify(publisher).writeAudit(audit);
    }

    @Test
    void ingestSetsRiskScoreOnEventBeforePublishing() {
        var fraudEngine = mock(FraudEngine.class);
        var publisher = mock(LogstashEventPublisher.class);

        var audit = new AuditEntry("au1", "ignored", "c1",
            List.of("HIGH_VALUE"), List.of("HIGH_VALUE"), 30, "ALLOW", Instant.now());
        var result = new EvaluationResult(List.of(), audit);

        when(fraudEngine.evaluate(any(EventDocument.class))).thenReturn(result);

        var controller = new EventController(fraudEngine, publisher);
        var request = new EventRequest("t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, null, Map.of());

        controller.ingest(request);

        verify(publisher).writeEvent(argThat(event -> event.riskScore() == 30));
    }

    @Test
    void ingestDefaultsEventTimeToNowWhenNull() {
        var fraudEngine = mock(FraudEngine.class);
        var publisher = mock(LogstashEventPublisher.class);

        var audit = new AuditEntry("au1", "ignored", "c1",
            List.of(), List.of(), 0, "ALLOW", Instant.now());
        when(fraudEngine.evaluate(any(EventDocument.class)))
            .thenReturn(new EvaluationResult(List.of(), audit));

        var controller = new EventController(fraudEngine, publisher);
        var request = new EventRequest("t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, null, Map.of());

        Instant before = Instant.now();
        controller.ingest(request);
        Instant after = Instant.now();

        verify(publisher).writeEvent(argThat(event ->
            !event.eventTime().isBefore(before) && !event.eventTime().isAfter(after)));
    }
}
