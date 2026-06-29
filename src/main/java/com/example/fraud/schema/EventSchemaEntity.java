package com.example.fraud.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "event_schemas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenantId", "eventType"})
})
@Getter
@Setter
@NoArgsConstructor
public class EventSchemaEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String eventType;

    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "JSON", nullable = false)
    private String fields;

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

    public List<SchemaFieldDefinition> getParsedFields() {
        try {
            return MAPPER.readValue(fields, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse schema fields", e);
        }
    }

    public void setFieldsFromList(List<SchemaFieldDefinition> fieldList) {
        try {
            this.fields = MAPPER.writeValueAsString(fieldList);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize schema fields", e);
        }
    }
}
