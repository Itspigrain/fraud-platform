package com.example.fraud.connector;

import com.example.fraud.config.CacheInvalidationPublisher;
import com.example.fraud.rule.RuleEntity;
import com.example.fraud.rule.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {

    @Mock private ConnectorRepository connectorRepository;
    @Mock private RuleRepository ruleRepository;
    @Mock private CacheInvalidationPublisher cacheInvalidationPublisher;

    private ConnectorService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorService(connectorRepository, ruleRepository, cacheInvalidationPublisher);
    }

    @Test
    void createConnectorSuccess() {
        var request = new ConnectorRequest(
            "test-webhook", "desc", ConnectorType.WEBHOOK, ConnectorStatus.ACTIVE,
            Map.of("url", "https://example.com/hook"), List.of(), 3, 1000
        );

        ConnectorEntity saved = new ConnectorEntity();
        saved.setId(1L);
        saved.setTenantId("t1");
        saved.setName("test-webhook");
        saved.setType(ConnectorType.WEBHOOK);
        saved.setStatus(ConnectorStatus.ACTIVE);
        saved.setConfigFromMap(Map.of("url", "https://example.com/hook"));
        saved.setRuleIdsFromList(List.of());
        saved.setRetryAttempts(3);
        saved.setRetryDelayMs(1000);

        when(connectorRepository.save(any())).thenReturn(saved);

        ConnectorResponse response = service.create("t1", request);

        assertThat(response.name()).isEqualTo("test-webhook");
        assertThat(response.type()).isEqualTo(ConnectorType.WEBHOOK);
        verify(cacheInvalidationPublisher).publishConnectorInvalidation("t1");
    }

    @Test
    void createRejectsBlankName() {
        var request = new ConnectorRequest(
            "", null, ConnectorType.WEBHOOK, null,
            Map.of("url", "https://example.com"), List.of(), null, null
        );

        assertThatThrownBy(() -> service.create("t1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("name is required");
    }

    @Test
    void createRejectsMissingUrl() {
        var request = new ConnectorRequest(
            "test", null, ConnectorType.WEBHOOK, null,
            Map.of(), List.of(), null, null
        );

        assertThatThrownBy(() -> service.create("t1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("config.url is required");
    }

    @Test
    void createRejectsMissingRuleIds() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.setTenantId("t1");

        when(ruleRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(rule));

        var request = new ConnectorRequest(
            "test", null, ConnectorType.WEBHOOK, null,
            Map.of("url", "https://example.com"), List.of(1L, 999L), null, null
        );

        assertThatThrownBy(() -> service.create("t1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Rule IDs not found");
    }

    @Test
    void createRejectsCrossTenantRules() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.setTenantId("other-tenant");

        when(ruleRepository.findAllById(List.of(1L))).thenReturn(List.of(rule));

        var request = new ConnectorRequest(
            "test", null, ConnectorType.WEBHOOK, null,
            Map.of("url", "https://example.com"), List.of(1L), null, null
        );

        assertThatThrownBy(() -> service.create("t1", request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("different tenant");
    }

    @Test
    void deleteConnectorSuccess() {
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId(1L);
        entity.setTenantId("t1");

        when(connectorRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete("t1", 1L);

        verify(connectorRepository).delete(entity);
        verify(cacheInvalidationPublisher).publishConnectorInvalidation("t1");
    }

    @Test
    void deleteRejectsWrongTenant() {
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId(1L);
        entity.setTenantId("other");

        when(connectorRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete("t1", 1L))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Connector not found");
    }

    @Test
    void getActiveConnectorsCachesResults() {
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId(1L);
        entity.setTenantId("t1");
        entity.setStatus(ConnectorStatus.ACTIVE);

        when(connectorRepository.findByTenantIdAndStatus("t1", ConnectorStatus.ACTIVE))
            .thenReturn(List.of(entity));

        List<ConnectorEntity> first = service.getActiveConnectors("t1");
        List<ConnectorEntity> second = service.getActiveConnectors("t1");

        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first);
        verify(connectorRepository, times(1)).findByTenantIdAndStatus("t1", ConnectorStatus.ACTIVE);
    }

    @Test
    void invalidateCacheForcesReload() {
        ConnectorEntity entity = new ConnectorEntity();
        entity.setId(1L);
        entity.setTenantId("t1");
        entity.setStatus(ConnectorStatus.ACTIVE);

        when(connectorRepository.findByTenantIdAndStatus("t1", ConnectorStatus.ACTIVE))
            .thenReturn(List.of(entity));

        service.getActiveConnectors("t1");
        service.invalidateCache("t1");
        service.getActiveConnectors("t1");

        verify(connectorRepository, times(2)).findByTenantIdAndStatus("t1", ConnectorStatus.ACTIVE);
    }
}
