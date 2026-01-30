package ru.tyomakr.akcp.core.model;

import java.time.Instant;
import java.util.UUID;

public record Job(
    UUID id,
    JobType type,
    JobStatus status,
    String payload,
    Instant createdAt,
    Instant updatedAt,
    String lastError
) {
}
