package com.example.fraud.config;

import com.example.fraud.rule.RuleService;
import com.example.fraud.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener implements MessageListener {

    private final RuleService ruleService;
    private final SchemaService schemaService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        if (CacheInvalidationPublisher.RULES_CHANNEL.equals(channel)) {
            log.debug("Received rule cache invalidation for tenant={}", body);
            ruleService.invalidateRulesCache(body);
        } else if (CacheInvalidationPublisher.SCHEMAS_CHANNEL.equals(channel)) {
            String[] parts = body.split(":", 2);
            if (parts.length == 2) {
                log.debug("Received schema cache invalidation for tenant={} eventType={}", parts[0], parts[1]);
                schemaService.invalidateSchemaCache(parts[0], parts[1]);
            }
        }
    }
}
