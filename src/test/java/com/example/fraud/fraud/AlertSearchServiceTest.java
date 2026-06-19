package com.example.fraud.fraud;

import com.example.fraud.search.SearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertSearchServiceTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final AlertSearchService service = new AlertSearchService(operations);

    private AlertDocument sampleAlert(String alertId, String customerId, String severity) {
        return new AlertDocument(alertId, "t1", "e1", customerId, "VELOCITY",
            severity, 30, "Too many transactions", Instant.parse("2026-06-16T12:00:00Z"));
    }

    private SearchHits<AlertDocument> mockHits(List<AlertDocument> docs, long total) {
        var hits = docs.stream()
            .map(doc -> new SearchHit<>("alerts", doc.alertId(), null, 1.0f,
                null, Map.of(), Map.of(), null, null, null, doc))
            .toList();
        return new SearchHitsImpl<>(total, TotalHitsRelation.EQUAL_TO, 1.0f,
            Duration.ZERO, null, null, hits, null, null, null);
    }

    @Test
    void searchWithNoFiltersReturnsAllAlerts() {
        var doc = sampleAlert("a1", "c1", "HIGH");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(hits);

        var request = new AlertSearchRequest(
            null, null, null, null, null,
            null, null, null, null,
            0, 20, "detectedAt", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().alertId()).isEqualTo("a1");
        assertThat(response.page().totalElements()).isEqualTo(1);
        assertThat(response.page().number()).isEqualTo(0);
        assertThat(response.page().size()).isEqualTo(20);
    }

    @Test
    void searchWithSeverityFilter() {
        var doc = sampleAlert("a1", "c1", "CRITICAL");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(hits);

        var request = new AlertSearchRequest(
            null, null, null, "CRITICAL", null,
            null, null, null, null,
            0, 20, "detectedAt", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().severity()).isEqualTo("CRITICAL");
    }

    @Test
    void searchWithFullTextQueryOnReason() {
        var doc = sampleAlert("a1", "c1", "HIGH");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(hits);

        var request = new AlertSearchRequest(
            "transactions", null, null, null, null,
            null, null, null, null,
            0, 20, "detectedAt", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithCombinedFilters() {
        var doc = sampleAlert("a1", "c1", "CRITICAL");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(hits);

        var request = new AlertSearchRequest(
            "transactions", "c1", "VELOCITY", "CRITICAL", null,
            10, 90,
            Instant.parse("2026-06-15T00:00:00Z"),
            Instant.parse("2026-06-17T00:00:00Z"),
            0, 10, "riskScore", "asc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.page().size()).isEqualTo(10);
    }

    @Test
    void searchReturnsEmptyWhenNoMatches() {
        var hits = mockHits(List.of(), 0);
        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(hits);

        var request = new AlertSearchRequest(
            null, "nonexistent", null, null, null,
            null, null, null, null,
            0, 20, "detectedAt", "desc");

        var response = service.search(request);

        assertThat(response.results()).isEmpty();
        assertThat(response.page().totalElements()).isEqualTo(0);
        assertThat(response.page().totalPages()).isEqualTo(0);
    }
}
