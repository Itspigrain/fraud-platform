package com.example.fraud.search;

import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventSearchRequest;
import com.example.fraud.fraud.AlertDocument;
import com.example.fraud.fraud.AlertSearchRequest;
import com.example.fraud.schema.SchemaIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import com.example.fraud.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AggregationService {

    private final ElasticsearchOperations operations;

    @Cacheable(value = "eventStats", key = "#request.toString()")
    public StatsResponse eventStats(EventSearchRequest request) {
        String tenantId = TenantContext.getTenantId();
        String indexName = SchemaIndexService.tenantIndexName(tenantId);

        var boolQuery = buildEventFilters(request);

        var query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(boolQuery)))
            .withAggregation("eventCountByType",
                Aggregation.of(a -> a.terms(t -> t.field("eventType"))))
            .withAggregation("eventsOverTime",
                Aggregation.of(a -> a.dateHistogram(d -> d.field("eventTime")
                    .calendarInterval(CalendarInterval.Day))))
            .withPageable(PageRequest.of(0, 1))
            .build();

        var hits = operations.search(query, EventDocument.class, IndexCoordinates.of(indexName));
        return new StatsResponse(parseAggregations(hits.getAggregations()));
    }

    @Cacheable(value = "alertStats", key = "#request.toString()")
    public StatsResponse alertStats(AlertSearchRequest request) {
        var boolQuery = buildAlertFilters(request);

        var query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(boolQuery)))
            .withAggregation("countBySeverity",
                Aggregation.of(a -> a.terms(t -> t.field("severity"))))
            .withAggregation("countByRule",
                Aggregation.of(a -> a.terms(t -> t.field("ruleId"))))
            .withAggregation("alertsOverTime",
                Aggregation.of(a -> a.dateHistogram(d -> d.field("detectedAt")
                    .calendarInterval(CalendarInterval.Day))))
            .withPageable(PageRequest.of(0, 1))
            .build();

        var hits = operations.search(query, AlertDocument.class);
        return new StatsResponse(parseAggregations(hits.getAggregations()));
    }

    private BoolQuery buildEventFilters(EventSearchRequest request) {
        return BoolQuery.of(b -> {
            addTermFilter(b, "eventType", request.eventType());
            addDateRange(b, "eventTime", request.from(), request.to());
            return b;
        });
    }

    private BoolQuery buildAlertFilters(AlertSearchRequest request) {
        return BoolQuery.of(b -> {
            if (!TenantContext.isSuperTenant()) {
                addTermFilter(b, "tenantId", TenantContext.getTenantId());
            }
            addTermFilter(b, "ruleId", request.ruleId());
            addTermFilter(b, "severity", request.severity());
            addTermFilter(b, "eventId", request.eventId());
            addDateRange(b, "detectedAt", request.from(), request.to());
            return b;
        });
    }

    private void addTermFilter(BoolQuery.Builder b, String field, String value) {
        if (value != null && !value.isBlank()) {
            b.filter(Query.of(q -> q.term(t -> t.field(field).value(value))));
        }
    }

    private void addDateRange(BoolQuery.Builder b, String field,
                              java.time.Instant from, java.time.Instant to) {
        if (from != null || to != null) {
            b.filter(Query.of(q -> q.range(r -> r.date(d -> {
                d.field(field);
                if (from != null) d.gte(from.toString());
                if (to != null) d.lte(to.toString());
                return d;
            }))));
        }
    }

    private Map<String, List<Map<String, Object>>> parseAggregations(
            Object aggregationsContainer) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        if (aggregationsContainer == null) return result;

        var esAggs = (ElasticsearchAggregations) aggregationsContainer;
        var aggMap = esAggs.aggregationsAsMap();

        for (var entry : aggMap.entrySet()) {
            var name = entry.getKey();
            var agg = entry.getValue().aggregation().getAggregate();
            result.put(name, parseBuckets(agg));
        }
        return result;
    }

    private List<Map<String, Object>> parseBuckets(Aggregate agg) {
        var buckets = new ArrayList<Map<String, Object>>();

        if (agg.isSterms()) {
            for (var bucket : agg.sterms().buckets().array()) {
                buckets.add(Map.of("key", bucket.key().stringValue(), "count", bucket.docCount()));
            }
        } else if (agg.isLterms()) {
            for (var bucket : agg.lterms().buckets().array()) {
                buckets.add(Map.of("key", bucket.key(), "count", bucket.docCount()));
            }
        } else if (agg.isHistogram()) {
            for (var bucket : agg.histogram().buckets().array()) {
                buckets.add(Map.of("key", (long) bucket.key(), "count", bucket.docCount()));
            }
        } else if (agg.isDateHistogram()) {
            for (var bucket : agg.dateHistogram().buckets().array()) {
                buckets.add(Map.of("key", bucket.keyAsString(), "count", bucket.docCount()));
            }
        }
        return buckets;
    }
}
