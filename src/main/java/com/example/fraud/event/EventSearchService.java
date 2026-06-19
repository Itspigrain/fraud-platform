package com.example.fraud.event;

import com.example.fraud.search.PageInfo;
import com.example.fraud.search.SearchResponse;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import com.example.fraud.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class EventSearchService {

    private final ElasticsearchOperations operations;

    public EventSearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public SearchResponse<EventDocument> search(EventSearchRequest request) {
        var boolQuery = BoolQuery.of(b -> {
            if (request.q() != null && !request.q().isBlank()) {
                b.must(Query.of(q -> q.multiMatch(m -> m
                    .query(request.q())
                    .fields("customerId", "email", "deviceId", "sourceIp", "eventType"))));
            }
            if (!TenantContext.isSuperTenant()) {
                addTermFilter(b, "tenantId", TenantContext.getTenantId());
            }
            addTermFilter(b, "customerId", request.customerId());
            addTermFilter(b, "eventType", request.eventType());
            addTermFilter(b, "sourceIp", request.sourceIp());
            addTermFilter(b, "deviceId", request.deviceId());
            addTermFilter(b, "email", request.email());
            if (request.riskScoreMin() != null || request.riskScoreMax() != null) {
                b.filter(Query.of(q -> q.range(r -> {
                    var nr = r.number(n -> {
                        n.field("riskScore");
                        if (request.riskScoreMin() != null) n.gte((double) request.riskScoreMin());
                        if (request.riskScoreMax() != null) n.lte((double) request.riskScoreMax());
                        return n;
                    });
                    return nr;
                })));
            }
            if (request.from() != null || request.to() != null) {
                b.filter(Query.of(q -> q.range(r -> r.date(d -> {
                    d.field("eventTime");
                    if (request.from() != null) d.gte(request.from().toString());
                    if (request.to() != null) d.lte(request.to().toString());
                    return d;
                }))));
            }
            return b;
        });

        var sort = Sort.by(
            "asc".equalsIgnoreCase(request.direction())
                ? Sort.Order.asc(request.sort())
                : Sort.Order.desc(request.sort()));
        var pageable = PageRequest.of(request.page(), request.size(), sort);

        var query = NativeQuery.builder()
            .withQuery(Query.of(q -> q.bool(boolQuery)))
            .withPageable(pageable)
            .build();

        var hits = operations.search(query, EventDocument.class);

        var results = hits.getSearchHits().stream()
            .map(h -> h.getContent())
            .toList();

        long totalElements = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / request.size());

        return new SearchResponse<>(results,
            new PageInfo(request.page(), request.size(), totalElements, totalPages));
    }

    private void addTermFilter(BoolQuery.Builder b, String field, String value) {
        if (value != null && !value.isBlank()) {
            b.filter(Query.of(q -> q.term(t -> t.field(field).value(value))));
        }
    }
}
