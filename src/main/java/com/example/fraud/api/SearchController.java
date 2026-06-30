package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventSearchRequest;
import com.example.fraud.event.EventSearchService;
import com.example.fraud.alert.AlertDocument;
import com.example.fraud.alert.AlertSearchRequest;
import com.example.fraud.alert.AlertSearchService;
import com.example.fraud.search.AggregationService;
import com.example.fraud.search.SearchResponse;
import com.example.fraud.search.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final EventSearchService eventSearchService;
    private final AlertSearchService alertSearchService;
    private final AggregationService aggregationService;

    @GetMapping("/events")
    public SearchResponse<EventDocument> searchEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new EventSearchRequest(q, eventType,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return eventSearchService.search(request);
    }

    @GetMapping("/alerts")
    public SearchResponse<AlertDocument> searchAlerts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new AlertSearchRequest(q, ruleId, severity,
            verdict, eventId,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return alertSearchService.search(request);
    }

    @GetMapping("/events/stats")
    public StatsResponse eventStats(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "eventTime") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new EventSearchRequest(q, eventType,
            parseInstant(from, "from"), parseInstant(to, "to"),
            page, size, sort, direction);

        return aggregationService.eventStats(request);
    }

    @GetMapping("/alerts/stats")
    public StatsResponse alertStats(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        var request = new AlertSearchRequest(q, ruleId, severity,
            verdict, eventId,
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
}
