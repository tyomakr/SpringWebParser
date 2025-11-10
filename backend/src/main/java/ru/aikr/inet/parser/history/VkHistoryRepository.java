package ru.aikr.inet.parser.history;

import java.time.Instant;

public interface VkHistoryRepository {

    boolean save(VkImageHistoryRecord record);

    java.util.List<VkImageHistoryRecord> findAll();

    boolean updateUseForTraining(long id, boolean useForTraining);

    long count();

    Instant lastSynced();
}
