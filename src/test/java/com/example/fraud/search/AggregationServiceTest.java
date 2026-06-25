package com.example.fraud.search;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventSearchRequest;
import com.example.fraud.fraud.AlertDocument;
import com.example.fraud.fraud.AlertSearchRequest;
import com.example.fraud.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AggregationServiceTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final AggregationService service = new AggregationService(operations);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void eventStatsReturnsAggregationBuckets() {
        var bucket = StringTermsBucket.of(b -> b.key("LOGIN").docCount(42));
        var stermsAgg = StringTermsAggregate.of(a -> a
            .buckets(Buckets.of(bu -> bu.array(List.of(bucket))))
            .sumOtherDocCount(0L)
            .docCountErrorUpperBound(0L));
        var aggregate = Aggregate.of(a -> a.sterms(stermsAgg));
        var esAggregations = new ElasticsearchAggregations(Map.of("eventCountByType", aggregate));

        @SuppressWarnings("unchecked")
        SearchHits<EventDocument> searchHits = mock(SearchHits.class);
        doReturn(esAggregations).when(searchHits).getAggregations();
        when(searchHits.getTotalHits()).thenReturn(42L);
        when(searchHits.getTotalHitsRelation()).thenReturn(TotalHitsRelation.EQUAL_TO);
        when(searchHits.getSearchHits()).thenReturn(List.of());

        when(operations.search(any(Query.class), eq(EventDocument.class), any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        var request = new EventSearchRequest(
            null, null, null, null, 0, 20, "eventTime", "desc");

        var response = service.eventStats(request);

        assertThat(response.aggregations()).containsKey("eventCountByType");
        var buckets = response.aggregations().get("eventCountByType");
        assertThat(buckets).hasSize(1);
        assertThat(buckets.getFirst().get("key")).isEqualTo("LOGIN");
        assertThat(buckets.getFirst().get("count")).isEqualTo(42L);
    }

    @Test
    void alertStatsReturnsAggregationBuckets() {
        var bucket = StringTermsBucket.of(b -> b.key("HIGH").docCount(15));
        var stermsAgg = StringTermsAggregate.of(a -> a
            .buckets(Buckets.of(bu -> bu.array(List.of(bucket))))
            .sumOtherDocCount(0L)
            .docCountErrorUpperBound(0L));
        var aggregate = Aggregate.of(a -> a.sterms(stermsAgg));
        var esAggregations = new ElasticsearchAggregations(Map.of("countBySeverity", aggregate));

        @SuppressWarnings("unchecked")
        SearchHits<AlertDocument> searchHits = mock(SearchHits.class);
        doReturn(esAggregations).when(searchHits).getAggregations();
        when(searchHits.getTotalHits()).thenReturn(15L);
        when(searchHits.getTotalHitsRelation()).thenReturn(TotalHitsRelation.EQUAL_TO);
        when(searchHits.getSearchHits()).thenReturn(List.of());

        when(operations.search(any(Query.class), eq(AlertDocument.class)))
            .thenReturn(searchHits);

        var request = new AlertSearchRequest(
            null, null, null, null,
            null, null,
            0, 20, "detectedAt", "desc");

        var response = service.alertStats(request);

        assertThat(response.aggregations()).containsKey("countBySeverity");
        var buckets = response.aggregations().get("countBySeverity");
        assertThat(buckets).hasSize(1);
        assertThat(buckets.getFirst().get("key")).isEqualTo("HIGH");
    }
}
