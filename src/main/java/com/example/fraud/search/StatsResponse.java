package com.example.fraud.search;

import java.util.List;
import java.util.Map;

public record StatsResponse(
    Map<String, List<Map<String, Object>>> aggregations
) {}
