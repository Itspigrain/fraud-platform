package com.example.fraud.event;

import java.time.Instant;
import java.util.Map;

public record EventRequest(
    String tenantId,
    String eventType,
    String customerId,
    String sourceIp,
    String deviceId,
    String email,
    String phoneNumber,
    Instant eventTime,
    Map<String, Object> attributes
) {}
