package com.example.fraud.config;

import com.example.fraud.rule.RuleService;
import com.example.fraud.schema.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationListenerTest {

    @Mock private RuleService ruleService;
    @Mock private SchemaService schemaService;
    @InjectMocks private CacheInvalidationListener listener;

    @Test
    void rulesChannelMessageInvalidatesRuleCache() {
        Message message = new DefaultMessage(
            CacheInvalidationPublisher.RULES_CHANNEL.getBytes(),
            "tenant-1".getBytes()
        );
        listener.onMessage(message, null);
        verify(ruleService).invalidateRulesCache("tenant-1");
    }

    @Test
    void schemasChannelMessageInvalidatesSchemaCache() {
        Message message = new DefaultMessage(
            CacheInvalidationPublisher.SCHEMAS_CHANNEL.getBytes(),
            "tenant-1:purchase".getBytes()
        );
        listener.onMessage(message, null);
        verify(schemaService).invalidateSchemaCache("tenant-1", "purchase");
    }

    @Test
    void unknownChannelIsIgnored() {
        Message message = new DefaultMessage(
            "fraud:cache:unknown".getBytes(),
            "data".getBytes()
        );
        listener.onMessage(message, null);
        verifyNoInteractions(ruleService);
        verifyNoInteractions(schemaService);
    }
}
