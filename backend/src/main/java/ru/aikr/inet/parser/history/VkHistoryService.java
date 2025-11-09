package ru.aikr.inet.parser.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    // 🔹 Новый метод — именно его не хватает
    public void recordPublication(VkImageHistoryRecord record) {
        repository.save(record);
    }

    protected List<VkImageHistoryRecord> fetchFromVk() {
        log.info("Fetching VK history for groupId={} (stub)", vkProperties.getGroupId());
        return Collections.emptyList();
    }
}
