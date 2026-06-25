package com.example.fraud.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
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
    @Field(type = FieldType.Date) Instant eventTime,
    Map<String, Object> attributes,
    int riskScore
) {
    public EventDocument withRiskScore(int riskScore) {
        return new EventDocument(id, tenantId, eventType, customerId, sourceIp,
            deviceId, email, phoneNumber, eventTime, attributes, riskScore);
    }
}
