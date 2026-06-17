package com.example.fraud.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import java.time.Instant;
import java.util.Map;

@Document(indexName = "events", createIndex = false)
public record EventDocument(
    @Id String id,
    String tenantId,
    String eventType,
    String customerId,
    String sourceIp,
    String deviceId,
    String email,
    String phoneNumber,
    Instant eventTime,
    Map<String, Object> attributes,
    int riskScore
) {
    public EventDocument withRiskScore(int riskScore) {
        return new EventDocument(id, tenantId, eventType, customerId, sourceIp,
            deviceId, email, phoneNumber, eventTime, attributes, riskScore);
    }
}
