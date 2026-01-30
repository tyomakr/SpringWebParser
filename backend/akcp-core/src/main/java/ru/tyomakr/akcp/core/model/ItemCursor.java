package ru.tyomakr.akcp.core.model;

import java.time.Instant;
import java.util.UUID;

public record ItemCursor(Instant createdAt, UUID id) {
}
