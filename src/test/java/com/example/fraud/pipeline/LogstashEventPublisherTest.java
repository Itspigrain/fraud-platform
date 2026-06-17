package com.example.fraud.pipeline;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.FraudAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogstashEventPublisherTest {

    @TempDir
    Path tempDir;

    private LogstashEventPublisher publisher;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        publisher = new LogstashEventPublisher(tempDir.toString(), mapper);
    }

    @Test
    void writeEventAppendsJsonLineToEventsFile() throws Exception {
        var event = new EventDocument("e1", "t1", "PAYMENT", "c1", "1.2.3.4",
            "d1", "a@b.com", "555-1234", Instant.parse("2026-06-16T12:00:00Z"),
            Map.of("amount", 15000), 30);

        publisher.writeEvent(event);

        Path eventsFile = tempDir.resolve("events.json");
        assertThat(eventsFile).exists();
        List<String> lines = Files.readAllLines(eventsFile);
        assertThat(lines).hasSize(1);
        var node = mapper.readTree(lines.get(0));
        assertThat(node.get("id").asText()).isEqualTo("e1");
        assertThat(node.get("customerId").asText()).isEqualTo("c1");
        assertThat(node.get("riskScore").asInt()).isEqualTo(30);
    }

    @Test
    void writeAlertAppendsJsonLineToAlertsFile() throws Exception {
        var alert = new FraudAlert("a1", "e1", "c1", "HIGH_VALUE", "HIGH",
            30, "exceeds threshold", Instant.parse("2026-06-16T12:00:00Z"));

        publisher.writeAlert(alert);

        Path alertsFile = tempDir.resolve("alerts.json");
        assertThat(alertsFile).exists();
        List<String> lines = Files.readAllLines(alertsFile);
        assertThat(lines).hasSize(1);
        var node = mapper.readTree(lines.get(0));
        assertThat(node.get("alertId").asText()).isEqualTo("a1");
        assertThat(node.get("ruleId").asText()).isEqualTo("HIGH_VALUE");
    }

    @Test
    void writeAuditAppendsJsonLineToAuditFile() throws Exception {
        var audit = new AuditEntry("au1", "e1", "c1",
            List.of("HIGH_VALUE", "VELOCITY"), List.of("HIGH_VALUE"),
            30, "ALLOW", Instant.parse("2026-06-16T12:00:00Z"));

        publisher.writeAudit(audit);

        Path auditFile = tempDir.resolve("audit.json");
        assertThat(auditFile).exists();
        List<String> lines = Files.readAllLines(auditFile);
        assertThat(lines).hasSize(1);
        var node = mapper.readTree(lines.get(0));
        assertThat(node.get("auditId").asText()).isEqualTo("au1");
        assertThat(node.get("decision").asText()).isEqualTo("ALLOW");
    }

    @Test
    void multipleWritesAppendToSameFile() throws Exception {
        var event1 = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            null, null, null, Instant.now(), Map.of(), 0);
        var event2 = new EventDocument("e2", "t1", "PAYMENT", "c2", "5.6.7.8",
            null, null, null, Instant.now(), Map.of(), 0);

        publisher.writeEvent(event1);
        publisher.writeEvent(event2);

        List<String> lines = Files.readAllLines(tempDir.resolve("events.json"));
        assertThat(lines).hasSize(2);
    }
}
