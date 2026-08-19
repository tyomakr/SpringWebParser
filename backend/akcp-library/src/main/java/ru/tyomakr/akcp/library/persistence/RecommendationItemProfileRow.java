package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationItemProfileRow(
    UUID attachmentId,
    UUID itemId,
    String sourceType,
    Instant itemCreatedAt,
    List<String> tags
) {
}
