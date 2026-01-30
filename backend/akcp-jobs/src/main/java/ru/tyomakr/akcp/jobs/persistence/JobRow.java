package ru.tyomakr.akcp.jobs.persistence;

import java.time.Instant;
import java.util.UUID;

public record JobRow(
    UUID id,
    String type,
    String status,
    String payload,
    Instant createdAt,
    Instant updatedAt,
    String lastError
) {
}
