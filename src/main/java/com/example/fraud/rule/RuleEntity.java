package com.example.fraud.rule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
public class RuleEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType = RuleType.CONDITION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleStatus status = RuleStatus.ACTIVE;

    private String verdict;

    private String severity;

    @Column(columnDefinition = "JSON")
    private String conditions;

    private String groupByField;

    private Integer timeWindowMinutes;

    private Integer threshold;

    @Column(columnDefinition = "JSON")
    private String dependsOn;

    @Enumerated(EnumType.STRING)
    private DependencyCondition dependencyCondition;

    @Column(columnDefinition = "TEXT")
    private String promptTemplate;

    private Integer evaluationIntervalMinutes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public List<RuleCondition> getParsedConditions() {
        try {
            return MAPPER.readValue(conditions, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse rule conditions", e);
        }
    }

    public void setConditionsFromList(List<RuleCondition> conditionList) {
        try {
            this.conditions = MAPPER.writeValueAsString(conditionList);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize rule conditions", e);
        }
    }

    public List<Long> getParsedDependsOn() {
        if (dependsOn == null || dependsOn.isBlank()) return List.of();
        try {
            return MAPPER.readValue(dependsOn, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setDependsOnFromList(List<Long> ids) {
        try {
            this.dependsOn = MAPPER.writeValueAsString(ids != null ? ids : List.of());
        } catch (Exception e) {
            this.dependsOn = "[]";
        }
    }
}
