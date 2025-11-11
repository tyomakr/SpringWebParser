package ru.aikr.inet.parser.history.repository;

import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;

import java.time.Instant;
import java.util.List;

public interface VkHistoryRepository {

    boolean save(VkImageHistoryRecord record);

    List<VkImageHistoryRecord> findAll();

    boolean updateUseForTraining(long id, boolean useForTraining);

    long count();

    Instant lastSynced();

    List<VkImageHistoryRecord> findTrainingBatch(int limit, int offset, Instant since);
}
