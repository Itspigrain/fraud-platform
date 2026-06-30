package com.example.fraud.alert;

import com.example.fraud.search.PageInfo;
import com.example.fraud.search.SearchResponse;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import com.example.fraud.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertSearchService {

    private final ElasticsearchOperations operations;

    public SearchResponse<AlertDocument> search(AlertSearchRequest request) {
        var boolQuery = BoolQuery.of(b -> {
            if (request.q() != null && !request.q().isBlank()) {
                b.must(Query.of(q -> q.multiMatch(m -> m
                    .query(request.q())
                    .fields("reason", "ruleId"))));
            }
            if (!TenantContext.isSuperTenant()) {
                addTermFilter(b, "tenantId", TenantContext.getTenantId());
            }
            addTermFilter(b, "ruleId", request.ruleId());
            addTermFilter(b, "severity", request.severity());
            addTermFilter(b, "verdict", request.verdict());
            addTermFilter(b, "eventId", request.eventId());
            if (request.from() != null || request.to() != null) {
                b.filter(Query.of(q -> q.range(r -> r.date(d -> {
                    d.field("detectedAt");
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

        var hits = operations.search(query, AlertDocument.class);

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
