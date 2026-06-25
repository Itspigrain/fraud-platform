package com.example.fraud.llm;

public interface LlmClient {
    LlmVerdict evaluate(String prompt, String eventsJson);
}
