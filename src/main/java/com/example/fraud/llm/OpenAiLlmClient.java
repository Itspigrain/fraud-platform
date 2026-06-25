package com.example.fraud.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper mapper;

    public OpenAiLlmClient(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        String baseUrl = properties.baseUrl() != null ? properties.baseUrl() : "https://api.openai.com/v1";
        var builder = RestClient.builder().baseUrl(baseUrl);
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiKey());
        }
        this.restClient = builder.build();
    }

    @Override
    public LlmVerdict evaluate(String prompt, String eventsJson) {
        String fullPrompt = prompt + "\n\nEvents to analyze:\n" + eventsJson
            + "\n\nRespond with JSON: {\"matched\": true/false, \"reasoning\": \"...\"}";

        Map<String, Object> requestBody = Map.of(
            "model", properties.model(),
            "max_tokens", properties.maxTokens(),
            "messages", List.of(
                Map.of("role", "system", "content", "You are a fraud detection analyst. Respond only with valid JSON."),
                Map.of("role", "user", "content", fullPrompt)
            )
        );

        try {
            String responseJson = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

            JsonNode root = mapper.readTree(responseJson);
            String content = root.at("/choices/0/message/content").asText();
            log.debug("Raw LLM response: {}", content);

            return parseVerdict(content, mapper);
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            return new LlmVerdict(false, "LLM API call failed: " + e.getMessage());
        }
    }

    static LlmVerdict parseVerdict(String content, ObjectMapper mapper) {
        try {
            String cleaned = content.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "").strip();
            }
            JsonNode node = mapper.readTree(cleaned);
            return new LlmVerdict(
                node.path("matched").asBoolean(false),
                node.path("reasoning").asText("No reasoning provided")
            );
        } catch (Exception e) {
            return new LlmVerdict(false, "Failed to parse LLM response: " + content);
        }
    }
}
