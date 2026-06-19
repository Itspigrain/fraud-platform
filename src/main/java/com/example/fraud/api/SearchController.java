package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventSearchRequest;
import com.example.fraud.event.EventSearchService;
import com.example.fraud.fraud.AlertDocument;
import com.example.fraud.fraud.AlertSearchRequest;
import com.example.fraud.fraud.AlertSearchService;
import com.example.fraud.search.AggregationService;
import com.example.fraud.search.SearchResponse;
import com.example.fraud.search.StatsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final EventSearchService eventSearchService;
    private final AlertSearchService alertSearchService;
    private final AggregationService aggregationService;

    public SearchController(EventSearchService eventSearchService,
                            AlertSearchService alertSearchService,
                            AggregationService aggregationService) {
        this.eventSearchService = eventSearchService;
        this.alertSearchService = alertSearchService;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/events")
    public SearchResponse<EventDocument> searchEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer riskScoreMin,
            @RequestParam(required = false) Integer riskScoreMax,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        validateRiskScore(riskScoreMin, riskScoreMax);
        validateSortField(sort, "id", "tenantId", "customerId", "eventType",
            "sourceIp", "deviceId", "email", "riskScore", "eventTime");

        var request = new EventSearchRequest(q, customerId, eventType,
            sourceIp, deviceId, email, riskScoreMin, riskScoreMax,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return eventSearchService.search(request);
    }

    @GetMapping("/alerts")
    public SearchResponse<AlertDocument> searchAlerts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Integer riskScoreMin,
            @RequestParam(required = false) Integer riskScoreMax,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        validateRiskScore(riskScoreMin, riskScoreMax);
        validateSortField(sort, "alertId", "eventId", "customerId", "ruleId",
            "severity", "riskScore", "detectedAt");

        var request = new AlertSearchRequest(q, customerId, ruleId, severity,
            eventId, riskScoreMin, riskScoreMax,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return alertSearchService.search(request);
    }

    @GetMapping("/events/stats")
    public StatsResponse eventStats(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer riskScoreMin,
            @RequestParam(required = false) Integer riskScoreMax,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new EventSearchRequest(q, customerId, eventType,
            sourceIp, deviceId, email, riskScoreMin, riskScoreMax,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return aggregationService.eventStats(request);
    }

    @GetMapping("/alerts/stats")
    public StatsResponse alertStats(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) Integer riskScoreMin,
            @RequestParam(required = false) Integer riskScoreMax,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new AlertSearchRequest(q, customerId, ruleId, severity,
            eventId, riskScoreMin, riskScoreMax,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return aggregationService.alertStats(request);
    }

    private Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid ISO-8601 date for '" + paramName + "': " + value);
        }
    }

    private void validateRiskScore(Integer min, Integer max) {
        if (min != null && (min < 0 || min > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "riskScoreMin must be between 0 and 100");
        }
        if (max != null && (max < 0 || max > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "riskScoreMax must be between 0 and 100");
        }
    }

    private void validateSortField(String sort, String... allowed) {
        for (var field : allowed) {
            if (field.equals(sort)) return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Invalid sort field: " + sort);
    }
}
