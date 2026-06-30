package com.example.fraud.alert;

import java.time.Instant;

public record AlertSearchRequest(
    String q,
    String ruleId,
    String severity,
    String verdict,
    String eventId,
    Instant from,
    Instant to,
    int page,
    int size,
    String sort,
    String direction
) {
    public AlertSearchRequest {
        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 100) size = 100;
        if (sort == null || sort.isBlank()) sort = "detectedAt";
        if (direction == null || direction.isBlank()) direction = "desc";
    }
}
