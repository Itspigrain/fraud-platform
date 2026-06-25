package com.example.fraud.llm;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NoOpLlmClientTest {

    private final NoOpLlmClient client = new NoOpLlmClient();

    @Test
    void evaluateAlwaysReturnsNotMatched() {
        LlmVerdict verdict = client.evaluate("detect fraud", "[{\"id\":\"1\"}]");

        assertThat(verdict.matched()).isFalse();
        assertThat(verdict.reasoning()).isEqualTo("LLM evaluation disabled");
    }
}
