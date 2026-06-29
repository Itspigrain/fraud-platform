package com.example.fraud.rule;

import com.example.fraud.event.EventDocument;
import com.example.fraud.search.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleResultsSearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final RuleIndexService indexService;

    public SearchResponse<EventDocument> search(String tenantId, Long ruleId,
                                                 int page, int size,
                                                 String sort, String direction) {
        String indexName = indexService.indexName(tenantId, ruleId);
        IndexCoordinates index = IndexCoordinates.of(indexName);

        if (!elasticsearchTemplate.indexOps(index).exists()) {
            return new SearchResponse<>(List.of(),
                new com.example.fraud.search.PageInfo(page, size, 0, 0));
        }

        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
            ? Sort.Direction.ASC : Sort.Direction.DESC;

        NativeQuery query = NativeQuery.builder()
            .withPageable(PageRequest.of(page, size, Sort.by(dir, sort)))
            .build();

        SearchHits<EventDocument> hits = elasticsearchTemplate.search(query, EventDocument.class, index);

        List<EventDocument> results = hits.getSearchHits().stream()
            .map(h -> h.getContent())
            .toList();

        long total = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) total / size);

        return new SearchResponse<>(results,
            new com.example.fraud.search.PageInfo(page, size, total, totalPages));
    }
}
