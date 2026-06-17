package com.example.fraud.search;

import com.example.fraud.event.EventDocument;
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
        var doc = new EventDocument("e1", "t1", "LOGIN", "c1", "1.2.3.4",
            "d1", "a@b.com", "555", Instant.parse("2026-06-16T12:00:00Z"),
            Map.of(), 10);
        var expected = new SearchResponse<>(List.of(doc),
            new PageInfo(0, 20, 1, 1));
        when(eventSearchService.search(any(EventSearchRequest.class))).thenReturn(expected);

        var response = controller.searchEvents(
            null, null, null, null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        assertThat(response.results()).hasSize(1);
        verify(eventSearchService).search(any(EventSearchRequest.class));
    }

    @Test
    void searchAlertsDelegatesToService() {
        var alert = new AlertDocument("a1", "e1", "c1", "VELOCITY",
            "HIGH", 30, "reason", Instant.parse("2026-06-16T12:00:00Z"));
        var expected = new SearchResponse<>(List.of(alert),
            new PageInfo(0, 20, 1, 1));
        when(alertSearchService.search(any(AlertSearchRequest.class))).thenReturn(expected);

        var response = controller.searchAlerts(
            null, null, null, null, null,
            null, null, null, null, 0, 20, "detectedAt", "desc");

        assertThat(response.results()).hasSize(1);
        verify(alertSearchService).search(any(AlertSearchRequest.class));
    }

    @Test
    void eventStatsDelegatesToAggregationService() {
        var expected = new StatsResponse(Map.of("eventCountByType", List.of()));
        when(aggregationService.eventStats(any(EventSearchRequest.class))).thenReturn(expected);

        var response = controller.eventStats(
            null, null, null, null, null, null, null,
            null, null, null, null, 0, 20, "eventTime", "desc");

        assertThat(response.aggregations()).containsKey("eventCountByType");
        verify(aggregationService).eventStats(any(EventSearchRequest.class));
    }

    @Test
    void alertStatsDelegatesToAggregationService() {
        var expected = new StatsResponse(Map.of("countBySeverity", List.of()));
        when(aggregationService.alertStats(any(AlertSearchRequest.class))).thenReturn(expected);

        var response = controller.alertStats(
            null, null, null, null, null,
            null, null, null, null, 0, 20, "detectedAt", "desc");

        assertThat(response.aggregations()).containsKey("countBySeverity");
        verify(aggregationService).alertStats(any(AlertSearchRequest.class));
    }

    @Test
    void searchEventsWithInvalidDateReturns400() {
        assertThrows(ResponseStatusException.class, () ->
            controller.searchEvents(null, null, null, null, null, null, null,
                null, null, "not-a-date", null, 0, 20, "eventTime", "desc"));
    }

    @Test
    void searchEventsWithInvalidSortFieldReturns400() {
        assertThrows(ResponseStatusException.class, () ->
            controller.searchEvents(null, null, null, null, null, null, null,
                null, null, null, null, 0, 20, "invalidField", "desc"));
    }

    @Test
    void searchEventsWithRiskScoreOutOfRangeReturns400() {
        assertThrows(ResponseStatusException.class, () ->
            controller.searchEvents(null, null, null, null, null, null, null,
                150, null, null, null, 0, 20, "eventTime", "desc"));
    }
}
