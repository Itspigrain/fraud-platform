package com.example.fraud.search;

import com.example.fraud.event.EventDocument;
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

class EventSearchServiceTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final EventSearchService service = new EventSearchService(operations);

    private EventDocument sampleEvent(String id, String customerId) {
        return new EventDocument(id, "t1", "LOGIN", customerId, "1.2.3.4",
            "d1", "a@b.com", "555", Instant.parse("2026-06-16T12:00:00Z"),
            Map.of(), 10);
    }

    private SearchHits<EventDocument> mockHits(List<EventDocument> docs, long total) {
        var hits = docs.stream()
            .map(doc -> new SearchHit<>("events", doc.id(), null, 1.0f,
                null, Map.of(), Map.of(), null, null, null, doc))
            .toList();
        return new SearchHitsImpl<>(total, TotalHitsRelation.EQUAL_TO, 1.0f,
            Duration.ZERO, null, null, hits, null, null, null);
    }

    @Test
    void searchWithNoFiltersReturnsAllEvents() {
        var doc = sampleEvent("e1", "c1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().id()).isEqualTo("e1");
        assertThat(response.page().totalElements()).isEqualTo(1);
        assertThat(response.page().number()).isEqualTo(0);
        assertThat(response.page().size()).isEqualTo(20);
    }

    @Test
    void searchWithCustomerIdFilterReturnsMatchingEvents() {
        var doc = sampleEvent("e1", "c1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, "c1", null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().customerId()).isEqualTo("c1");
    }

    @Test
    void searchWithFullTextQueryUsesMultiMatch() {
        var doc = sampleEvent("e1", "alice");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            "alice", null, null, null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithRiskScoreRangeFilters() {
        var doc = sampleEvent("e1", "c1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, null, null, null,
            20, 80, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithDateRangeFilters() {
        var doc = sampleEvent("e1", "c1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, null, null, null,
            null, null,
            Instant.parse("2026-06-15T00:00:00Z"),
            Instant.parse("2026-06-17T00:00:00Z"),
            0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithCombinedFiltersAndFullText() {
        var doc = sampleEvent("e1", "alice");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            "alice", "t1", null, "LOGIN", null, null, null,
            0, 50,
            Instant.parse("2026-06-15T00:00:00Z"), null,
            0, 10, "riskScore", "asc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.page().size()).isEqualTo(10);
    }

    @Test
    void searchReturnsEmptyWhenNoMatches() {
        var hits = mockHits(List.of(), 0);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, "nonexistent", null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).isEmpty();
        assertThat(response.page().totalElements()).isEqualTo(0);
        assertThat(response.page().totalPages()).isEqualTo(0);
    }

    @Test
    void searchCalculatesTotalPagesCorrectly() {
        var doc = sampleEvent("e1", "c1");
        var hits = mockHits(List.of(doc), 45);
        when(operations.search(any(Query.class), eq(EventDocument.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, null, null, null,
            null, null, null, null, 2, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.page().totalPages()).isEqualTo(3);
        assertThat(response.page().number()).isEqualTo(2);
    }
}
