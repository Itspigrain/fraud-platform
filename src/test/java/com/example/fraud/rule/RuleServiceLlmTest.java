package com.example.fraud.rule;

import com.example.fraud.config.CacheInvalidationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleServiceLlmTest {

    @Mock private RuleRepository ruleRepository;
    @Mock private RuleEvaluationService evaluationService;
    @Mock private RuleIndexService indexService;
    @Mock private CacheInvalidationPublisher cacheInvalidationPublisher;
    @InjectMocks private RuleService ruleService;

    @Test
    void createLlmEvaluatorRuleStoresPromptAndInterval() {
        var request = new RuleRequest(
            "purchase", "Card testing detector", "Detects card testing",
            RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE,
            null, null, 30, null,
            "Analyze for card-testing patterns", 5,
            null, null
        );

        when(ruleRepository.save(any())).thenAnswer(inv -> {
            RuleEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        RuleResponse response = ruleService.create("tenant1", request);

        assertThat(response.ruleType()).isEqualTo(RuleType.LLM_EVALUATOR);
        assertThat(response.promptTemplate()).isEqualTo("Analyze for card-testing patterns");
        assertThat(response.evaluationIntervalMinutes()).isEqualTo(5);
        assertThat(response.timeWindowMinutes()).isEqualTo(30);

        ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
        verify(ruleRepository).save(captor.capture());
        RuleEntity saved = captor.getValue();
        assertThat(saved.getConditions()).isEqualTo("[]");
        assertThat(saved.getPromptTemplate()).isEqualTo("Analyze for card-testing patterns");
        assertThat(saved.getEvaluationIntervalMinutes()).isEqualTo(5);
    }

    @Test
    void createPublishesCacheInvalidation() {
        var request = new RuleRequest(
            "purchase", "Test Rule", "desc",
            RuleType.CONDITION, RuleStatus.ACTIVE,
            List.of(new RuleCondition("amount", ConditionOperator.GREATER_THAN, "100")),
            null, null, null, null, null, null, null
        );

        when(ruleRepository.save(any())).thenAnswer(inv -> {
            RuleEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });

        ruleService.create("tenant-1", request);

        verify(cacheInvalidationPublisher).publishRuleInvalidation("tenant-1");
    }
}
