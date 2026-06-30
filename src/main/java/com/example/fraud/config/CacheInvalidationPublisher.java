package com.example.fraud.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    static final String RULES_CHANNEL = "fraud:cache:rules";
    static final String SCHEMAS_CHANNEL = "fraud:cache:schemas";

    private final StringRedisTemplate redisTemplate;

    public void publishRuleInvalidation(String tenantId) {
        try {
            redisTemplate.convertAndSend(RULES_CHANNEL, tenantId);
            log.debug("Published rule cache invalidation for tenant={}", tenantId);
        } catch (Exception e) {
            log.warn("Failed to publish rule cache invalidation for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    public void publishSchemaInvalidation(String tenantId, String eventType) {
        try {
            redisTemplate.convertAndSend(SCHEMAS_CHANNEL, tenantId + ":" + eventType);
            log.debug("Published schema cache invalidation for tenant={} eventType={}", tenantId, eventType);
        } catch (Exception e) {
            log.warn("Failed to publish schema cache invalidation for tenant={} eventType={}: {}",
                tenantId, eventType, e.getMessage());
        }
    }
}
