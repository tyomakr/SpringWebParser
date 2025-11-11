package ru.aikr.inet.parser.history.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.history.model.VkHistoryTrainingExportResponse;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.history.model.VkHistoryStats;
import ru.aikr.inet.parser.history.model.VkProperties;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VkHistoryService {

    private final VkHistoryRepository repository;
    private final VkProperties vkProperties;

    public VkHistoryStats refreshFromVk() {
        List<VkImageHistoryRecord> fetched = fetchFromVk();
        int updated = 0;
        for (VkImageHistoryRecord record : fetched) {
            boolean saved = repository.save(record);
            if (saved) {
                updated++;
            }
        }
        long total = repository.count();
        Instant lastSynced = repository.lastSynced();
        return new VkHistoryStats(total, updated, lastSynced);
    }

    public VkHistoryStats currentStats() {
        return new VkHistoryStats(repository.count(), 0, repository.lastSynced());
    }

    public void recordPublication(VkImageHistoryRecord record, String mlDecision, Double mlScore, String mlReason) {
        record.setMlDecision(mlDecision);
        record.setMlScore(mlScore);
        record.setMlReason(mlReason);
        repository.save(record);
    }

    public List<VkImageHistoryRecord> getHistoryEntries() {
        return repository.findAll();
    }

    public List<VkImageHistoryRecord> getTrainingEntries() {
        return repository.findAll().stream()
                .filter(record -> Boolean.TRUE.equals(record.getUseForTraining()))
                .toList();
    }

    public void updateUseForTraining(long id, boolean useForTraining) {
        repository.updateUseForTraining(id, useForTraining);
    }

    public List<VkHistoryTrainingExportResponse> exportTraining(int limit, int offset, Instant since) {
        int safeLimit = Math.max(limit, 1);
        int safeOffset = Math.max(offset, 0);
        return repository.findTrainingBatch(safeLimit, safeOffset, since).stream()
                .map(VkHistoryTrainingExportResponse::fromRecord)
                .toList();
    }

    protected List<VkImageHistoryRecord> fetchFromVk() {
        log.info("Fetching VK history for groupId={} (stub)", vkProperties.getGroupId());
        return Collections.emptyList();
    }
}
