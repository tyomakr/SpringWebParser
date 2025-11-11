package ru.aikr.inet.parser.history.model;

import java.time.Instant;

public record VkHistoryStats(long totalCount, long updatedCount, Instant lastSynced) {
}
