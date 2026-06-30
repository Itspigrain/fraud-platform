package com.example.fraud.connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectorRepository extends JpaRepository<ConnectorEntity, Long> {

    List<ConnectorEntity> findByTenantId(String tenantId);

    List<ConnectorEntity> findByTenantIdAndStatus(String tenantId, ConnectorStatus status);
}
