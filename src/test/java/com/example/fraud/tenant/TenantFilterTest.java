package com.example.fraud.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantFilterTest {

    private TenantFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TenantFilter("X-Tenant-Id", "__super__", List.of("/actuator/health"));
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setsTenantContextFromHeader() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-abc");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-abc");
        });

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void returns400WhenHeaderMissing() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void returns400WhenHeaderBlank() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "   ");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void skipsFilterForExcludedPaths() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void recognizesSuperTenant() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "__super__");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(TenantContext.getTenantId()).isEqualTo("__super__");
            assertThat(TenantContext.isSuperTenant()).isTrue();
        });
    }

    @Test
    void clearsTenantContextAfterRequest() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-abc");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void clearsTenantContextEvenOnException() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-abc");
        var response = new MockHttpServletResponse();

        doThrow(new RuntimeException("boom")).when(chain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException ignored) {}

        assertThat(TenantContext.getTenantId()).isNull();
    }
}
