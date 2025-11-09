package ru.aikr.inet.parser.history;

import java.time.Instant;

public interface VkHistoryRepository {

    boolean save(VkImageHistoryRecord record);

    long count();

    Instant lastSynced();
}