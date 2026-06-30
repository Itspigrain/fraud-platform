package com.example.fraud.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @InjectMocks private CacheInvalidationPublisher publisher;

    @Test
    void publishRuleInvalidationSendsToRulesChannel() {
        publisher.publishRuleInvalidation("tenant-1");
        verify(redisTemplate).convertAndSend("fraud:cache:rules", "tenant-1");
    }

    @Test
    void publishSchemaInvalidationSendsToSchemasChannel() {
        publisher.publishSchemaInvalidation("tenant-1", "purchase");
        verify(redisTemplate).convertAndSend("fraud:cache:schemas", "tenant-1:purchase");
    }

    @Test
    void publishRuleInvalidationSwallowsRedisFailure() {
        doThrow(new RuntimeException("Redis down")).when(redisTemplate).convertAndSend(anyString(), anyString());
        assertThatCode(() -> publisher.publishRuleInvalidation("tenant-1")).doesNotThrowAnyException();
    }

    @Test
    void publishSchemaInvalidationSwallowsRedisFailure() {
        doThrow(new RuntimeException("Redis down")).when(redisTemplate).convertAndSend(anyString(), anyString());
        assertThatCode(() -> publisher.publishSchemaInvalidation("tenant-1", "purchase")).doesNotThrowAnyException();
    }
}
