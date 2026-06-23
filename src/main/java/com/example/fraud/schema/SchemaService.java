package com.example.fraud.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final EventSchemaRepository repository;
    private final SchemaValidationService validationService;
    private final SchemaIndexService indexService;

    private final Map<String, EventSchemaEntity> schemaCache = new ConcurrentHashMap<>();

    public SchemaResponse create(String tenantId, SchemaRequest request) {
        if (repository.existsByTenantIdAndEventType(tenantId, request.eventType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Schema already exists for event type: " + request.eventType());
        }

        validationService.validateSchemaDefinition(request.fields());

        EventSchemaEntity entity = new EventSchemaEntity();
        entity.setTenantId(tenantId);
        entity.setEventType(request.eventType());
        entity.setDisplayName(request.displayName());
        entity.setDescription(request.description());
        entity.setFieldsFromList(request.fields());

        entity = repository.save(entity);
        indexService.ensureTenantIndex(tenantId, request.fields());
        invalidateCache(tenantId, request.eventType());

        log.info("Created schema tenant={} eventType={}", tenantId, request.eventType());
        return SchemaResponse.from(entity);
    }

    public List<SchemaResponse> listByTenant(String tenantId) {
        return repository.findByTenantId(tenantId).stream()
            .map(SchemaResponse::from)
            .toList();
    }

    public SchemaResponse getByEventType(String tenantId, String eventType) {
        return SchemaResponse.from(findEntityOrThrow(tenantId, eventType));
    }

    public SchemaResponse update(String tenantId, String eventType, SchemaRequest request) {
        EventSchemaEntity entity = findEntityOrThrow(tenantId, eventType);

        validationService.validateSchemaDefinition(request.fields());

        if (request.displayName() != null) entity.setDisplayName(request.displayName());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.fields() != null) {
            entity.setFieldsFromList(request.fields());
            indexService.ensureTenantIndex(tenantId, request.fields());
        }

        entity = repository.save(entity);
        invalidateCache(tenantId, eventType);

        log.info("Updated schema tenant={} eventType={}", tenantId, eventType);
        return SchemaResponse.from(entity);
    }

    public void delete(String tenantId, String eventType) {
        EventSchemaEntity entity = findEntityOrThrow(tenantId, eventType);
        repository.delete(entity);
        invalidateCache(tenantId, eventType);
        log.info("Deleted schema tenant={} eventType={}", tenantId, eventType);
    }

    public List<SchemaFieldDefinition> getFieldDefinitions(String tenantId, String eventType) {
        return findEntityOrThrow(tenantId, eventType).getParsedFields();
    }

    public Optional<EventSchemaEntity> findSchema(String tenantId, String eventType) {
        String key = tenantId + ":" + eventType;
        EventSchemaEntity cached = schemaCache.get(key);
        if (cached != null) return Optional.of(cached);

        Optional<EventSchemaEntity> found = repository.findByTenantIdAndEventType(tenantId, eventType);
        found.ifPresent(e -> schemaCache.put(key, e));
        return found;
    }

    private void invalidateCache(String tenantId, String eventType) {
        schemaCache.remove(tenantId + ":" + eventType);
    }

    private EventSchemaEntity findEntityOrThrow(String tenantId, String eventType) {
        return repository.findByTenantIdAndEventType(tenantId, eventType)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Schema not found for event type: " + eventType));
    }
}
