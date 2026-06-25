package com.example.fraud.llm;

import com.example.fraud.event.EventDocument;
import com.example.fraud.fraud.FraudAlert;
import com.example.fraud.pipeline.LogstashEventPublisher;
import com.example.fraud.rule.*;
import com.example.fraud.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmRuleSchedulerTest {

    @Mock private RuleRepository ruleRepository;
    @Mock private LlmClient llmClient;
    @Mock private LogstashEventPublisher publisher;
    @Mock private ElasticsearchTemplate elasticsearchTemplate;

    private LlmRuleScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LlmRuleScheduler(ruleRepository, llmClient, publisher, elasticsearchTemplate);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evaluatesRuleAndCreatesAlertsWhenMatched() {
        RuleEntity rule = buildLlmRule("tenant1", "purchase", "Detect fraud", 30, 5);

        when(ruleRepository.findByRuleTypeAndStatus(RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE))
            .thenReturn(List.of(rule));

        EventDocument event = new EventDocument("evt1", "tenant1", "purchase", Instant.now(), Map.of("amount", 100));
        mockSearchResults(event);

        when(llmClient.evaluate(any(), any()))
            .thenReturn(new LlmVerdict(true, "Card-testing pattern detected"));

        scheduler.tick();

        ArgumentCaptor<FraudAlert> alertCaptor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(publisher).writeAlert(alertCaptor.capture());

        FraudAlert alert = alertCaptor.getValue();
        assertThat(alert.ruleId()).isEqualTo("Detect fraud");
        assertThat(alert.reason()).contains("Detect fraud");
        assertThat(alert.reason()).contains("Card-testing pattern detected");
        assertThat(alert.eventId()).isEqualTo("evt1");
        assertThat(alert.severity()).isEqualTo("HIGH");
        assertThat(alert.tenantId()).isEqualTo("tenant1");
    }

    @Test
    void skipsEvaluationWhenNoEventsFound() {
        RuleEntity rule = buildLlmRule("tenant1", "purchase", "Detect fraud", 30, 5);

        when(ruleRepository.findByRuleTypeAndStatus(RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE))
            .thenReturn(List.of(rule));

        mockEmptySearchResults();

        scheduler.tick();

        verifyNoInteractions(llmClient);
        verify(publisher, never()).writeAlert(any());
    }

    @Test
    void skipsRuleNotYetDueForEvaluation() {
        RuleEntity rule = buildLlmRule("tenant1", "purchase", "Detect fraud", 30, 60);

        when(ruleRepository.findByRuleTypeAndStatus(RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE))
            .thenReturn(List.of(rule));

        EventDocument event = new EventDocument("evt1", "tenant1", "purchase", Instant.now(), Map.of());
        mockSearchResults(event);
        when(llmClient.evaluate(any(), any())).thenReturn(new LlmVerdict(false, "OK"));

        scheduler.tick();
        scheduler.tick();

        verify(llmClient, times(1)).evaluate(any(), any());
    }

    @Test
    void doesNotCreateAlertWhenLlmReturnsNotMatched() {
        RuleEntity rule = buildLlmRule("tenant1", "purchase", "Detect fraud", 30, 5);

        when(ruleRepository.findByRuleTypeAndStatus(RuleType.LLM_EVALUATOR, RuleStatus.ACTIVE))
            .thenReturn(List.of(rule));

        EventDocument event = new EventDocument("evt1", "tenant1", "purchase", Instant.now(), Map.of());
        mockSearchResults(event);

        when(llmClient.evaluate(any(), any()))
            .thenReturn(new LlmVerdict(false, "Normal activity"));

        scheduler.tick();

        verify(publisher, never()).writeAlert(any());
    }

    @SuppressWarnings("unchecked")
    private void mockSearchResults(EventDocument... events) {
        SearchHits<EventDocument> searchHits = mock(SearchHits.class);
        List<SearchHit<EventDocument>> hitList = new java.util.ArrayList<>();
        for (EventDocument event : events) {
            SearchHit<EventDocument> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(event);
            hitList.add(hit);
        }
        when(searchHits.getSearchHits()).thenReturn(hitList);
        when(elasticsearchTemplate.search(any(Query.class), eq(EventDocument.class), any())).thenReturn(searchHits);
    }

    @SuppressWarnings("unchecked")
    private void mockEmptySearchResults() {
        SearchHits<EventDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchTemplate.search(any(Query.class), eq(EventDocument.class), any())).thenReturn(searchHits);
    }

    private RuleEntity buildLlmRule(String tenantId, String eventType, String name, int timeWindow, int interval) {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.setTenantId(tenantId);
        rule.setEventType(eventType);
        rule.setName(name);
        rule.setRuleType(RuleType.LLM_EVALUATOR);
        rule.setStatus(RuleStatus.ACTIVE);
        rule.setPromptTemplate("Analyze for fraud patterns");
        rule.setTimeWindowMinutes(timeWindow);
        rule.setEvaluationIntervalMinutes(interval);
        return rule;
    }
}
