package com.example.fraud.rule;

import com.example.fraud.config.CacheInvalidationPublisher;
import com.example.fraud.event.EventDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RuleEvaluationService evaluationService;
    private final RuleIndexService indexService;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    private final Map<String, List<RuleEntity>> activeRulesCache = new ConcurrentHashMap<>();

    public RuleResponse create(String tenantId, RuleRequest request) {
        RuleEntity entity = new RuleEntity();
        entity.setTenantId(tenantId);
        entity.setEventType(request.eventType());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setRuleType(request.ruleType() != null ? request.ruleType() : RuleType.CONDITION);
        entity.setStatus(request.status() != null ? request.status() : RuleStatus.ACTIVE);
        entity.setVerdict(request.verdict());
        entity.setSeverity(request.severity());

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
        RuleEntity entity = findByIdAndTenant(tenantId, ruleId);

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

    private RuleEntity findByIdAndTenant(String tenantId, Long ruleId) {
        return ruleRepository.findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Rule not found: " + ruleId));
    }
}
