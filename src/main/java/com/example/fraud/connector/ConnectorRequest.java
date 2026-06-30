package com.example.fraud.connector;

import java.util.List;
import java.util.Map;

public record ConnectorRequest(
    String name,
    String description,
    ConnectorType type,
    ConnectorStatus status,
    Map<String, Object> config,
    List<Long> ruleIds,
    Integer retryAttempts,
    Integer retryDelayMs
) {}
