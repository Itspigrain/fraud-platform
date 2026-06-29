package com.example.fraud.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseValidMatchedResponse() {
        String json = """
            {"matched": true, "reasoning": "Suspicious pattern detected"}
            """;

        LlmVerdict verdict = OpenAiLlmClient.parseVerdict(json, mapper);

        assertThat(verdict.matched()).isTrue();
        assertThat(verdict.reasoning()).isEqualTo("Suspicious pattern detected");
    }

    @Test
    void parseValidNotMatchedResponse() {
        String json = """
            {"matched": false, "reasoning": "Normal activity"}
            """;

        LlmVerdict verdict = OpenAiLlmClient.parseVerdict(json, mapper);

        assertThat(verdict.matched()).isFalse();
        assertThat(verdict.reasoning()).isEqualTo("Normal activity");
    }

    @Test
    void parseMalformedResponseReturnsNotMatched() {
        LlmVerdict verdict = OpenAiLlmClient.parseVerdict("not json", mapper);

        assertThat(verdict.matched()).isFalse();
        assertThat(verdict.reasoning()).startsWith("Failed to parse LLM response");
    }

    @Test
    void parseResponseWithMarkdownCodeFence() {
        String json = """
            ```json
            {"matched": true, "reasoning": "Fraud detected"}
            ```
            """;

        LlmVerdict verdict = OpenAiLlmClient.parseVerdict(json, mapper);

        assertThat(verdict.matched()).isTrue();
        assertThat(verdict.reasoning()).isEqualTo("Fraud detected");
    }
}
