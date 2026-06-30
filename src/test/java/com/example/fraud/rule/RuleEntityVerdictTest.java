package com.example.fraud.rule;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEntityVerdictTest {

    @Test
    void verdictAndSeverityDefaultToNull() {
        RuleEntity rule = new RuleEntity();
        assertThat(rule.getVerdict()).isNull();
        assertThat(rule.getSeverity()).isNull();
    }

    @Test
    void verdictAndSeverityCanBeSet() {
        RuleEntity rule = new RuleEntity();
        rule.setVerdict("BLOCK");
        rule.setSeverity("CRITICAL");
        assertThat(rule.getVerdict()).isEqualTo("BLOCK");
        assertThat(rule.getSeverity()).isEqualTo("CRITICAL");
    }
}
