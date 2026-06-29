package com.example.fraud.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "none", matchIfMissing = true)
public class NoOpLlmClient implements LlmClient {

    @Override
    public LlmVerdict evaluate(String prompt, String eventsJson) {
        return new LlmVerdict(false, "LLM evaluation disabled");
    }
}
