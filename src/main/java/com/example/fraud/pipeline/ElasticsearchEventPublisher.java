package com.example.fraud.pipeline;

import com.example.fraud.audit.AuditEntry;
import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.FraudAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchEventPublisher {

    private final ElasticsearchOperations operations;

    public void writeEvent(EventDocument event) {
        try {
            operations.index(new IndexQueryBuilder().withId(event.id()).withObject(event).build(),
                IndexCoordinates.of("events"));
        } catch (Exception e) {
            log.error("Failed to index event {}: {}", event.id(), e.getMessage());
        }
    }

    public void writeAlert(FraudAlert alert) {
        try {
            operations.index(new IndexQueryBuilder().withId(alert.alertId()).withObject(alert).build(),
                IndexCoordinates.of("alerts"));
        } catch (Exception e) {
            log.error("Failed to index alert {}: {}", alert.alertId(), e.getMessage());
        }
    }

    public void writeAudit(AuditEntry audit) {
        try {
            operations.index(new IndexQueryBuilder().withId(audit.auditId()).withObject(audit).build(),
                IndexCoordinates.of("audit"));
        } catch (Exception e) {
            log.error("Failed to index audit {}: {}", audit.auditId(), e.getMessage());
        }
    }
}
