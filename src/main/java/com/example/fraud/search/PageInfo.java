package com.example.fraud.search;

public record PageInfo(
    int number,
    int size,
    long totalElements,
    int totalPages
) {}
