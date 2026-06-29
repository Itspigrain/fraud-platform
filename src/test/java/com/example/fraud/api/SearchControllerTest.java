package com.example.fraud.api;

import com.example.fraud.event.EventDocument;
import com.example.fraud.event.EventSearchRequest;
import com.example.fraud.event.EventSearchService;
import com.example.fraud.fraud.AlertDocument;
import com.example.fraud.fraud.AlertSearchRequest;
import com.example.fraud.fraud.AlertSearchService;
import com.example.fraud.search.AggregationService;
import com.example.fraud.search.PageInfo;
import com.example.fraud.search.SearchResponse;
import com.example.fraud.search.StatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SearchControllerTest {

    private final EventSearchService eventSearchService = mock(EventSearchService.class);
    private final AlertSearchService alertSearchService = mock(AlertSearchService.class);
    private final AggregationService aggregationService = mock(AggregationService.class);
    private final SearchController controller = new SearchController(
        eventSearchService, alertSearchService, aggregationService);

    @Test
    void searchEventsDelegatesToService() {
        var doc = new EventDocument("e1", "t1", "LOGIN",
            Instant.parse("2026-06-16T12:00:00Z"), Map.of());
        var expected = new SearchResponse<>(List.of(doc),
            new PageInfo(0, 20, 1, 1));
        when(eventSearchService.search(any(EventSearchRequest.class))).thenReturn(expected);

        var response = controller.searchEvents(
            null, null, null, null, 0, 20, "eventTime", "desc");

        assertThat(response.results()).hasSize(1);
        verify(eventSearchService).search(any(EventSearchRequest.class));
    }

    @Test
    void searchAlertsDelegatesToService() {
        var alert = new AlertDocument("a1", "t1", "e1", "VELOCITY",
            "HIGH", "reason", Instant.parse("2026-06-16T12:00:00Z"));
        var expected = new SearchResponse<>(List.of(alert),
            new PageInfo(0, 20, 1, 1));
        when(alertSearchService.search(any(AlertSearchRequest.class))).thenReturn(expected);

        var response = controller.searchAlerts(
            null, null, null, null, null, null,
            0, 20, "detectedAt", "desc");

        assertThat(response.results()).hasSize(1);
        verify(alertSearchService).search(any(AlertSearchRequest.class));
    }

    @Test
    void eventStatsDelegatesToAggregationService() {
        var expected = new StatsResponse(Map.of("eventCountByType", List.of()));
        when(aggregationService.eventStats(any(EventSearchRequest.class))).thenReturn(expected);

        var response = controller.eventStats(
            null, null, null, null, 0, 20, "eventTime", "desc");

        assertThat(response.aggregations()).containsKey("eventCountByType");
        verify(aggregationService).eventStats(any(EventSearchRequest.class));
    }

    @Test
    void searchEventsWithInvalidDateReturns400() {
        assertThrows(ResponseStatusException.class, () ->
            controller.searchEvents(null, null, "not-a-date", null,
                0, 20, "eventTime", "desc"));
    }
}
