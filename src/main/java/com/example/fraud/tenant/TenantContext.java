package com.example.fraud.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static volatile String superTenantValue = "__super__";

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static String getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }

    public static boolean isSuperTenant() {
        var current = TENANT_ID.get();
        return current != null && current.equals(superTenantValue);
    }

    public static void setSuperTenantValue(String value) {
        superTenantValue = value;
    }
}
