package com.example.fraud.event;

import java.time.Instant;
import java.util.Map;

public record EventRequest(
    String eventType,
    Instant eventTime,
    Map<String, Object> attributes
) {}
