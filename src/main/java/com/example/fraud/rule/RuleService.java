package com.example.fraud.rule;

import com.example.fraud.config.CacheInvalidationPublisher;
import com.example.fraud.event.EventDocument;
import com.example.fraud.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RuleEvaluationService evaluationService;
    private final RuleIndexService indexService;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final RuleValidationService validationService;
    private final SchemaService schemaService;

    private final Map<String, List<RuleEntity>> activeRulesCache = new ConcurrentHashMap<>();

    public List<RuleValidationService.ValidationError> validateRule(String tenantId, RuleRequest request) {
        var schema = schemaService.findSchema(tenantId, request.eventType());
        if (schema.isEmpty()) {
            return List.of(new RuleValidationService.ValidationError(
                "eventType", "No schema registered for event type '" + request.eventType() + "'"));
        }
        return validationService.validate(request, schema.get().getParsedFields());
    }

    public RuleResponse create(String tenantId, RuleRequest request) {
        rejectIfInvalid(tenantId, request);
        if (request.dependsOn() != null && !request.dependsOn().isEmpty()) {
            validateDependencies(tenantId, request.eventType(), null, request.dependsOn());
        }

        RuleEntity entity = new RuleEntity();
        entity.setTenantId(tenantId);
        entity.setEventType(request.eventType());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setRuleType(request.ruleType() != null ? request.ruleType() : RuleType.CONDITION);
        entity.setStatus(request.status() != null ? request.status() : RuleStatus.ACTIVE);
        entity.setVerdict(request.verdict());
        entity.setSeverity(request.severity());
        entity.setDependsOnFromList(request.dependsOn());
        entity.setDependencyCondition(request.dependencyCondition());

        if (entity.getRuleType() == RuleType.CONDITION) {
            entity.setConditionsFromList(request.conditions());
        } else if (entity.getRuleType() == RuleType.LLM_EVALUATOR) {
            entity.setConditions("[]");
            entity.setPromptTemplate(request.promptTemplate());
            entity.setTimeWindowMinutes(request.timeWindowMinutes());
            entity.setEvaluationIntervalMinutes(request.evaluationIntervalMinutes());
        } else {
            entity.setConditions("[]");
            entity.setGroupByField(request.groupByField());
            entity.setTimeWindowMinutes(request.timeWindowMinutes());
            entity.setThreshold(request.threshold());
        }

        entity = ruleRepository.save(entity);
        indexService.createIndex(tenantId, entity.getId());
        invalidateRulesCache(tenantId);
        cacheInvalidationPublisher.publishRuleInvalidation(tenantId);

        log.info("Created rule id={} name={} tenant={} eventType={}", entity.getId(), entity.getName(), tenantId, request.eventType());
        return RuleResponse.from(entity);
    }

    public List<RuleResponse> listByTenant(String tenantId) {
        return ruleRepository.findByTenantId(tenantId).stream()
            .map(RuleResponse::from)
            .toList();
    }

    public RuleResponse getById(String tenantId, Long ruleId) {
        RuleEntity entity = findByIdAndTenant(tenantId, ruleId);
        return RuleResponse.from(entity);
    }

    public RuleResponse update(String tenantId, Long ruleId, RuleRequest request) {
        rejectIfInvalid(tenantId, request);
        RuleEntity entity = findByIdAndTenant(tenantId, ruleId);

        String effectiveEventType = request.eventType() != null ? request.eventType() : entity.getEventType();
        if (request.dependsOn() != null && !request.dependsOn().isEmpty()) {
            validateDependencies(tenantId, effectiveEventType, ruleId, request.dependsOn());
        }

        if (request.eventType() != null) entity.setEventType(request.eventType());
        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.status() != null) entity.setStatus(request.status());
        if (request.ruleType() != null) entity.setRuleType(request.ruleType());
        if (request.conditions() != null) entity.setConditionsFromList(request.conditions());
        if (request.groupByField() != null) entity.setGroupByField(request.groupByField());
        if (request.timeWindowMinutes() != null) entity.setTimeWindowMinutes(request.timeWindowMinutes());
        if (request.threshold() != null) entity.setThreshold(request.threshold());
        if (request.promptTemplate() != null) entity.setPromptTemplate(request.promptTemplate());
        if (request.evaluationIntervalMinutes() != null) entity.setEvaluationIntervalMinutes(request.evaluationIntervalMinutes());
        if (request.verdict() != null) entity.setVerdict(request.verdict());
        if (request.severity() != null) entity.setSeverity(request.severity());
        if (request.dependsOn() != null) entity.setDependsOnFromList(request.dependsOn());
        if (request.dependencyCondition() != null) entity.setDependencyCondition(request.dependencyCondition());

        entity = ruleRepository.save(entity);
        invalidateRulesCache(tenantId);
        cacheInvalidationPublisher.publishRuleInvalidation(tenantId);

        log.info("Updated rule id={} tenant={}", ruleId, tenantId);
        return RuleResponse.from(entity);
    }

    public void delete(String tenantId, Long ruleId, boolean deleteIndex) {
        RuleEntity entity = findByIdAndTenant(tenantId, ruleId);
        ruleRepository.delete(entity);

        if (deleteIndex) {
            indexService.deleteIndex(tenantId, ruleId);
        }

        invalidateRulesCache(tenantId);
        cacheInvalidationPublisher.publishRuleInvalidation(tenantId);
        log.info("Deleted rule id={} tenant={} indexDeleted={}", ruleId, tenantId, deleteIndex);
    }

    public List<RuleEntity> evaluateEvent(String tenantId, String eventType, EventDocument event) {
        List<RuleEntity> activeRules = getActiveRules(tenantId, eventType);
        if (activeRules.isEmpty()) {
            return List.of();
        }

        List<RuleEntity> matched = evaluationService.evaluate(event, activeRules);

        for (RuleEntity rule : matched) {
            indexService.indexEvent(tenantId, rule.getId(), event);
        }

        return matched;
    }

    private List<RuleEntity> getActiveRules(String tenantId, String eventType) {
        String cacheKey = tenantId + ":" + eventType;
        return activeRulesCache.computeIfAbsent(cacheKey,
            k -> ruleRepository.findByTenantIdAndEventTypeAndStatus(tenantId, eventType, RuleStatus.ACTIVE));
    }

    public void invalidateRulesCache(String tenantId) {
        activeRulesCache.keySet().removeIf(key -> key.startsWith(tenantId + ":"));
    }

    private void validateDependencies(String tenantId, String eventType, Long ruleId, List<Long> dependsOn) {
        List<RuleEntity> depRules = ruleRepository.findAllById(dependsOn);

        Set<Long> foundIds = depRules.stream().map(RuleEntity::getId).collect(Collectors.toSet());
        List<Long> missing = dependsOn.stream().filter(id -> !foundIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Dependency rules not found: " + missing);
        }

        for (RuleEntity dep : depRules) {
            if (!dep.getTenantId().equals(tenantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dependency rule " + dep.getId() + " belongs to a different tenant");
            }
            if (!dep.getEventType().equals(eventType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dependency rule " + dep.getId() + " has different eventType: " + dep.getEventType());
            }
        }

        if (ruleId != null) {
            detectCircularDependency(ruleId, dependsOn);
        }
    }

    private void detectCircularDependency(Long ruleId, List<Long> dependsOn) {
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new LinkedList<>(dependsOn);

        while (!queue.isEmpty()) {
            Long depId = queue.poll();
            if (depId.equals(ruleId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Circular dependency detected involving rule " + ruleId);
            }
            if (visited.add(depId)) {
                ruleRepository.findById(depId).ifPresent(dep ->
                    queue.addAll(dep.getParsedDependsOn()));
            }
        }
    }

    private void rejectIfInvalid(String tenantId, RuleRequest request) {
        List<RuleValidationService.ValidationError> errors = validateRule(tenantId, request);
        if (!errors.isEmpty()) {
            String message = errors.stream()
                .map(e -> e.field() + ": " + e.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private RuleEntity findByIdAndTenant(String tenantId, Long ruleId) {
        return ruleRepository.findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Rule not found: " + ruleId));
    }
}
