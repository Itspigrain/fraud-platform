package com.example.fraud.event;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EventRepository extends ElasticsearchRepository<EventDocument, String> {
}
