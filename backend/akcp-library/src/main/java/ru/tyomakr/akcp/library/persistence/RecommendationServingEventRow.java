package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.UUID;

public record RecommendationServingEventRow(
    UUID id,
    String username,
    UUID referenceAttachmentId,
    String experimentGroup,
    int requestedLimit,
    int returnedCount,
    String candidatesJson,
    Long latencyMs,
    Instant createdAt
) {
}
