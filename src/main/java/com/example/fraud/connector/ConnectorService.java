package com.example.fraud.connector;

import com.example.fraud.config.CacheInvalidationPublisher;
import com.example.fraud.rule.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorService {

    private final ConnectorRepository connectorRepository;
    private final RuleRepository ruleRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    private final Map<String, List<ConnectorEntity>> activeConnectorsCache = new ConcurrentHashMap<>();

    public ConnectorResponse create(String tenantId, ConnectorRequest request) {
        validate(tenantId, request);

        ConnectorEntity entity = new ConnectorEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setType(request.type() != null ? request.type() : ConnectorType.WEBHOOK);
        entity.setStatus(request.status() != null ? request.status() : ConnectorStatus.ACTIVE);
        entity.setConfigFromMap(request.config());
        entity.setRuleIdsFromList(request.ruleIds());
        entity.setRetryAttempts(request.retryAttempts() != null ? request.retryAttempts() : 3);
        entity.setRetryDelayMs(request.retryDelayMs() != null ? request.retryDelayMs() : 1000);

        entity = connectorRepository.save(entity);
        invalidateCache(tenantId);
        cacheInvalidationPublisher.publishConnectorInvalidation(tenantId);

        log.info("Created connector id={} name={} tenant={}", entity.getId(), entity.getName(), tenantId);
        return ConnectorResponse.from(entity);
    }

    public List<ConnectorResponse> listByTenant(String tenantId) {
        return connectorRepository.findByTenantId(tenantId).stream()
            .map(ConnectorResponse::from)
            .toList();
    }

    public ConnectorResponse getById(String tenantId, Long id) {
        ConnectorEntity entity = findByIdAndTenant(tenantId, id);
        return ConnectorResponse.from(entity);
    }

    public ConnectorResponse update(String tenantId, Long id, ConnectorRequest request) {
        ConnectorEntity entity = findByIdAndTenant(tenantId, id);
        validate(tenantId, request);

        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.type() != null) entity.setType(request.type());
        if (request.status() != null) entity.setStatus(request.status());
        if (request.config() != null) entity.setConfigFromMap(request.config());
        if (request.ruleIds() != null) entity.setRuleIdsFromList(request.ruleIds());
        if (request.retryAttempts() != null) entity.setRetryAttempts(request.retryAttempts());
        if (request.retryDelayMs() != null) entity.setRetryDelayMs(request.retryDelayMs());

        entity = connectorRepository.save(entity);
        invalidateCache(tenantId);
        cacheInvalidationPublisher.publishConnectorInvalidation(tenantId);

        log.info("Updated connector id={} tenant={}", id, tenantId);
        return ConnectorResponse.from(entity);
    }

    public void delete(String tenantId, Long id) {
        ConnectorEntity entity = findByIdAndTenant(tenantId, id);
        connectorRepository.delete(entity);
        invalidateCache(tenantId);
        cacheInvalidationPublisher.publishConnectorInvalidation(tenantId);
        log.info("Deleted connector id={} tenant={}", id, tenantId);
    }

    public List<ConnectorEntity> getActiveConnectors(String tenantId) {
        return activeConnectorsCache.computeIfAbsent(tenantId,
            k -> connectorRepository.findByTenantIdAndStatus(tenantId, ConnectorStatus.ACTIVE));
    }

    public void invalidateCache(String tenantId) {
        activeConnectorsCache.remove(tenantId);
    }

    private void validate(String tenantId, ConnectorRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.config() == null || !request.config().containsKey("url")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config.url is required");
        }
        Object url = request.config().get("url");
        if (!(url instanceof String) || ((String) url).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config.url must be a non-empty string");
        }

        if (request.ruleIds() != null && !request.ruleIds().isEmpty()) {
            var foundRules = ruleRepository.findAllById(request.ruleIds());
            Set<Long> foundIds = foundRules.stream()
                .map(r -> r.getId())
                .collect(Collectors.toSet());
            List<Long> missing = request.ruleIds().stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rule IDs not found: " + missing);
            }
            for (var rule : foundRules) {
                if (!rule.getTenantId().equals(tenantId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rule " + rule.getId() + " belongs to a different tenant");
                }
            }
        }
    }

    private ConnectorEntity findByIdAndTenant(String tenantId, Long id) {
        return connectorRepository.findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Connector not found: " + id));
    }
}
