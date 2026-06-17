package com.example.fraud.search;

import java.util.List;

public record SearchResponse<T>(
    List<T> results,
    PageInfo page
) {}
