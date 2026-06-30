package com.example.fraud.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "connectors")
@Getter
@Setter
@NoArgsConstructor
public class ConnectorEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorType type = ConnectorType.WEBHOOK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorStatus status = ConnectorStatus.ACTIVE;

    @Column(columnDefinition = "JSON")
    private String config;

    @Column(columnDefinition = "JSON")
    private String ruleIds;

    @Column(nullable = false)
    private Integer retryAttempts = 3;

    @Column(nullable = false)
    private Integer retryDelayMs = 1000;

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

    public Map<String, Object> getParsedConfig() {
        if (config == null || config.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    public void setConfigFromMap(Map<String, Object> configMap) {
        try {
            this.config = MAPPER.writeValueAsString(configMap != null ? configMap : Map.of());
        } catch (Exception e) {
            this.config = "{}";
        }
    }

    public List<Long> getParsedRuleIds() {
        if (ruleIds == null || ruleIds.isBlank()) return List.of();
        try {
            return MAPPER.readValue(ruleIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setRuleIdsFromList(List<Long> ids) {
        try {
            this.ruleIds = MAPPER.writeValueAsString(ids != null ? ids : List.of());
        } catch (Exception e) {
            this.ruleIds = "[]";
        }
    }
}
