package ru.aikr.inet.parser.history.repository;

import org.springframework.data.domain.Page;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;

import java.time.Instant;
import java.util.List;

public interface VkHistoryRepository {

    boolean save(VkImageHistoryRecord record);

    boolean saveIfAbsent(VkImageHistoryRecord record);

    boolean existsByHash(String hash);

    List<VkImageHistoryRecord> findAll();

    boolean updateUseForTraining(long id, boolean useForTraining);

    long count();

    long count(Boolean useForTraining, Instant since);

    Instant lastSynced();

    List<VkImageHistoryRecord> findTrainingBatch(int limit, int offset, Instant since);

    Page<VkImageHistoryRecord> findPage(int limit, int offset, Boolean useForTraining, Instant since);
}
