package com.example.fraud.connector;

import com.example.fraud.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorService connectorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorResponse create(@RequestBody ConnectorRequest request) {
        return connectorService.create(TenantContext.getTenantId(), request);
    }

    @GetMapping
    public List<ConnectorResponse> list() {
        return connectorService.listByTenant(TenantContext.getTenantId());
    }

    @GetMapping("/{id}")
    public ConnectorResponse getById(@PathVariable Long id) {
        return connectorService.getById(TenantContext.getTenantId(), id);
    }

    @PutMapping("/{id}")
    public ConnectorResponse update(@PathVariable Long id, @RequestBody ConnectorRequest request) {
        return connectorService.update(TenantContext.getTenantId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        connectorService.delete(TenantContext.getTenantId(), id);
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        ConnectorResponse connector = connectorService.getById(tenantId, id);
        Map<String, Object> config = connector.config();

        String url = (String) config.get("url");
        String method = config.containsKey("method") ? (String) config.get("method") : "POST";
        int timeoutMs = config.containsKey("timeoutMs")
            ? ((Number) config.get("timeoutMs")).intValue()
            : 5000;

        String samplePayload = """
            {"eventId":"test-000","eventType":"test","tenantId":"%s","eventTime":"2026-01-01T00:00:00Z","attributes":{},"rule":{"id":0,"name":"test-rule","verdict":"REVIEW","severity":"HIGH","description":"Test webhook"},"alert":{"alertId":"test-alert-000","detectedAt":"2026-01-01T00:00:00Z"},"connector":{"id":%d,"name":"%s"}}""".formatted(
            tenantId, connector.id(), connector.name());

        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json");

            @SuppressWarnings("unchecked")
            Map<String, String> headers = config.containsKey("headers")
                ? (Map<String, String>) config.get("headers")
                : Map.of();
            headers.forEach(requestBuilder::header);

            HttpRequest request2 = switch (method.toUpperCase()) {
                case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(samplePayload)).build();
                default -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(samplePayload)).build();
            };

            HttpResponse<String> response = client.send(request2, HttpResponse.BodyHandlers.ofString());

            result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
            result.put("statusCode", response.statusCode());
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        result.put("responseTimeMs", System.currentTimeMillis() - start);
        return result;
    }
}
