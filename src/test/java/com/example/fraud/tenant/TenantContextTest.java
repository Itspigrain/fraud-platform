package com.example.fraud.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setAndGetTenantId() {
        TenantContext.setTenantId("tenant-abc");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-abc");
    }

    @Test
    void clearRemovesTenantId() {
        TenantContext.setTenantId("tenant-abc");
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void isSuperTenantReturnsTrueForSuperValue() {
        TenantContext.setSuperTenantValue("__super__");
        TenantContext.setTenantId("__super__");
        assertThat(TenantContext.isSuperTenant()).isTrue();
    }

    @Test
    void isSuperTenantReturnsFalseForNormalTenant() {
        TenantContext.setSuperTenantValue("__super__");
        TenantContext.setTenantId("tenant-abc");
        assertThat(TenantContext.isSuperTenant()).isFalse();
    }

    @Test
    void isSuperTenantReturnsFalseWhenNoTenantSet() {
        TenantContext.setSuperTenantValue("__super__");
        assertThat(TenantContext.isSuperTenant()).isFalse();
    }
}
