package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.UUID;

public record ItemRow(
    UUID id,
    String title,
    String content,
    String sourceType,
    String sourceUrl,
    Instant createdAt,
    Instant updatedAt
) {
}
