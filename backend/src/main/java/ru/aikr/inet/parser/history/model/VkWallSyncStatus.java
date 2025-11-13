package ru.aikr.inet.parser.history.model;

import java.time.Duration;
import java.time.Instant;

public record VkWallSyncStatus(
        boolean running,
        Instant lastRun,
        VkWallSyncReport lastReport,
        String lastError,
        Instant backoffUntil,
        Instant lastSince,
        Duration rateLimit
) {}
