package ru.tyomakr.akcp.library.dto;

public record RecommendationBackfillResponse(
    int scanned,
    int upserted,
    long durationMs
) {
}
