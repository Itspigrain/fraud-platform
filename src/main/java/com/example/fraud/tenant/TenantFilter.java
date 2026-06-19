package com.example.fraud.tenant;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantFilter implements Filter {

    private final String headerName;
    private final String superTenantValue;
    private final List<String> excludedPaths;

    @Autowired
    public TenantFilter(TenantProperties properties) {
        this(properties.headerName(), properties.superTenantValue(), properties.excludedPaths());
    }

    TenantFilter(String headerName, String superTenantValue, List<String> excludedPaths) {
        this.headerName = headerName;
        this.superTenantValue = superTenantValue;
        this.excludedPaths = excludedPaths;
        TenantContext.setSuperTenantValue(superTenantValue);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        if (isExcluded(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = httpRequest.getHeader(headerName);
        if (tenantId == null || tenantId.isBlank()) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("Missing required header: " + headerName);
            return;
        }

        try {
            TenantContext.setTenantId(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcluded(String path) {
        return excludedPaths.stream().anyMatch(path::startsWith);
    }
}
