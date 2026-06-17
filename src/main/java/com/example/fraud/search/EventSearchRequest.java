package com.example.fraud.search;

import java.time.Instant;

public record EventSearchRequest(
    String q,
    String tenantId,
    String customerId,
    String eventType,
    String sourceIp,
    String deviceId,
    String email,
    Integer riskScoreMin,
    Integer riskScoreMax,
    Instant from,
    Instant to,
    int page,
    int size,
    String sort,
    String direction
) {
    public EventSearchRequest {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;
        if (sort == null || sort.isBlank()) sort = "eventTime";
        if (direction == null || direction.isBlank()) direction = "desc";
    }
}
