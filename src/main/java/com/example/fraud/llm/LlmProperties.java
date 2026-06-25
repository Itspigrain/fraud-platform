package com.example.fraud.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    String provider,
    String baseUrl,
    String apiKey,
    String model,
    int maxTokens
) {}
