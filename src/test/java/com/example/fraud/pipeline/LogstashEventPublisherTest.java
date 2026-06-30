package com.example.fraud.pipeline;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
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
        var event = new EventDocument("e1", "t1", "PAYMENT",
            Instant.parse("2026-06-16T12:00:00Z"),
            Map.of("amount", 15000, "customerId", "c1"));

        publisher.writeEvent(event);

        Path eventsFile = tempDir.resolve("events.json");
        assertThat(eventsFile).exists();
        List<String> lines = Files.readAllLines(eventsFile);
        assertThat(lines).hasSize(1);
        var node = mapper.readTree(lines.get(0));
        assertThat(node.get("id").asText()).isEqualTo("e1");
        assertThat(node.get("eventType").asText()).isEqualTo("PAYMENT");
    }

    @Test
    void writeAuditAppendsJsonLineToAuditFile() throws Exception {
        var audit = new AuditEntry("au1", "t1", "e1",
            List.of("HIGH_VALUE", "VELOCITY"), List.of("HIGH_VALUE"),
            List.of(Map.of("rule", "HIGH_VALUE", "verdict", "REVIEW", "severity", "HIGH", "reason", "amount > 10000")),
            Instant.parse("2026-06-16T12:00:00Z"));

        publisher.writeAudit(audit);

        Path auditFile = tempDir.resolve("audit.json");
        assertThat(auditFile).exists();
        List<String> lines = Files.readAllLines(auditFile);
        assertThat(lines).hasSize(1);
        var node = mapper.readTree(lines.get(0));
        assertThat(node.get("auditId").asText()).isEqualTo("au1");
        assertThat(node.get("verdicts").isArray()).isTrue();
    }

    @Test
    void multipleWritesAppendToSameFile() throws Exception {
        var event1 = new EventDocument("e1", "t1", "LOGIN",
            Instant.now(), Map.of());
        var event2 = new EventDocument("e2", "t1", "PAYMENT",
            Instant.now(), Map.of());

        publisher.writeEvent(event1);
        publisher.writeEvent(event2);

        List<String> lines = Files.readAllLines(tempDir.resolve("events.json"));
        assertThat(lines).hasSize(2);
    }
}
