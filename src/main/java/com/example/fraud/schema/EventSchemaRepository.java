package com.example.fraud.schema;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventSchemaRepository extends JpaRepository<EventSchemaEntity, Long> {
    Optional<EventSchemaEntity> findByTenantIdAndEventType(String tenantId, String eventType);
    List<EventSchemaEntity> findByTenantId(String tenantId);
    boolean existsByTenantIdAndEventType(String tenantId, String eventType);

    @Query("SELECT DISTINCT e.tenantId FROM EventSchemaEntity e ORDER BY e.tenantId")
    List<String> findDistinctTenantIds();
}
