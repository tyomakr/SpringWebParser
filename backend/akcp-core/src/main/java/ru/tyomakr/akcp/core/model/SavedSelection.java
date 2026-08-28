package ru.tyomakr.akcp.core.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedSelection(
    UUID id,
    String username,
    UUID itemId,
    List<UUID> attachmentIds,
    String target,
    Instant createdAt,
    Instant expiresAt
) {
}
