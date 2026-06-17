package com.example.fraud.event;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends ElasticsearchRepository<EventDocument, String> {

    List<EventDocument> findByCustomerIdAndEventTimeAfter(String customerId, Instant after);

    Optional<EventDocument> findFirstByCustomerIdOrderByEventTimeDesc(String customerId);
}
