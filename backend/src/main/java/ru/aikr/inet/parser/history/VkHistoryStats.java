package ru.aikr.inet.parser.history;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class VkHistoryStats {

    private final long totalCount;
    private final long updatedCount;
    private final Instant lastSynced;
}