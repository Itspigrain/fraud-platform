package com.example.fraud.connector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class WebhookExecutor {

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Async("connectorExecutor")
    public void execute(ConnectorEntity connector, String payload) {
        Map<String, Object> config = connector.getParsedConfig();
        String url = (String) config.get("url");
        String method = config.containsKey("method") ? (String) config.get("method") : "POST";
        int timeoutMs = config.containsKey("timeoutMs")
            ? ((Number) config.get("timeoutMs")).intValue()
            : 5000;

        int maxAttempts = connector.getRetryAttempts() != null ? connector.getRetryAttempts() : 3;
        int baseDelayMs = connector.getRetryDelayMs() != null ? connector.getRetryDelayMs() : 1000;

        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = (long) baseDelayMs * (1L << (attempt - 1));
                    Thread.sleep(delay);
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");

                @SuppressWarnings("unchecked")
                Map<String, String> headers = config.containsKey("headers")
                    ? (Map<String, String>) config.get("headers")
                    : Map.of();
                headers.forEach(requestBuilder::header);

                HttpRequest request = switch (method.toUpperCase()) {
                    case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(payload)).build();
                    default -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                };

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Webhook success connector={} url={} status={} attempt={}",
                        connector.getId(), url, response.statusCode(), attempt + 1);
                    return;
                }

                log.warn("Webhook non-success connector={} url={} status={} attempt={}/{}",
                    connector.getId(), url, response.statusCode(), attempt + 1, maxAttempts + 1);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Webhook interrupted connector={} url={}", connector.getId(), url);
                return;
            } catch (Exception e) {
                log.warn("Webhook error connector={} url={} attempt={}/{}: {}",
                    connector.getId(), url, attempt + 1, maxAttempts + 1, e.getMessage());
            }
        }

        log.warn("Webhook exhausted retries connector={} url={} after {} attempts",
            connector.getId(), url, maxAttempts + 1);
    }
}
