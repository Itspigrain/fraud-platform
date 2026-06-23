package com.example.fraud.rule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleRepository extends JpaRepository<RuleEntity, Long> {

    List<RuleEntity> findByTenantId(String tenantId);

    List<RuleEntity> findByTenantIdAndStatus(String tenantId, RuleStatus status);
}
