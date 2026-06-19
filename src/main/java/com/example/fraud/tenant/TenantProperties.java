package com.example.fraud.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tenant")
public record TenantProperties(
    String headerName,
    String superTenantValue,
    List<String> excludedPaths
) {
    public TenantProperties {
        if (headerName == null) headerName = "X-Tenant-Id";
        if (superTenantValue == null) superTenantValue = "__super__";
        if (excludedPaths == null) excludedPaths = List.of();
    }
}
