package com.example.fraud.event;

import com.example.fraud.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
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

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EventDocument sampleEvent(String id) {
        return new EventDocument(id, "t1", "LOGIN",
            Instant.parse("2026-06-16T12:00:00Z"), Map.of("customerId", "c1"));
    }

    private SearchHits<EventDocument> mockHits(List<EventDocument> docs, long total) {
        var hits = docs.stream()
            .map(doc -> new SearchHit<>("events-t1", doc.id(), null, 1.0f,
                null, Map.of(), Map.of(), null, null, null, doc))
            .toList();
        return new SearchHitsImpl<>(total, TotalHitsRelation.EQUAL_TO, 1.0f,
            Duration.ZERO, null, null, hits, null, null, null);
    }

    @Test
    void searchWithNoFiltersReturnsAllEvents() {
        var doc = sampleEvent("e1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class), any(IndexCoordinates.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().id()).isEqualTo("e1");
        assertThat(response.page().totalElements()).isEqualTo(1);
    }

    @Test
    void searchWithEventTypeFilter() {
        var doc = sampleEvent("e1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class), any(IndexCoordinates.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, "LOGIN", null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchWithDateRangeFilters() {
        var doc = sampleEvent("e1");
        var hits = mockHits(List.of(doc), 1);
        when(operations.search(any(Query.class), eq(EventDocument.class), any(IndexCoordinates.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null,
            Instant.parse("2026-06-15T00:00:00Z"),
            Instant.parse("2026-06-17T00:00:00Z"),
            0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).hasSize(1);
    }

    @Test
    void searchReturnsEmptyWhenNoMatches() {
        var hits = mockHits(List.of(), 0);
        when(operations.search(any(Query.class), eq(EventDocument.class), any(IndexCoordinates.class)))
            .thenReturn(hits);

        var request = new EventSearchRequest(
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.search(request);

        assertThat(response.results()).isEmpty();
        assertThat(response.page().totalElements()).isEqualTo(0);
    }
}
