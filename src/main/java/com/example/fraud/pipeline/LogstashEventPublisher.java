package com.example.fraud.pipeline;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LogstashEventPublisher {

    private final Path logDir;
    private final ObjectMapper mapper;

    public LogstashEventPublisher(
            @Value("${fraud.pipeline.log-dir:logs}") String logDir,
            ObjectMapper mapper) {
        this.logDir = Path.of(logDir);
        this.mapper = mapper;
    }

    public void writeEvent(EventDocument event) {
        appendJson("events.json", event);
    }

    public void writeAudit(AuditEntry audit) {
        appendJson("audit.json", audit);
    }

    private void appendJson(String filename, Object obj) {
        try {
            Files.createDirectories(logDir);
            Path file = logDir.resolve(filename);
            try (var writer = new PrintWriter(new FileWriter(file.toFile(), true))) {
                writer.println(mapper.writeValueAsString(obj));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write to " + filename, e);
        }
    }
}
