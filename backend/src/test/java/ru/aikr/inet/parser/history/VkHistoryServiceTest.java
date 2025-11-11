package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.aikr.inet.parser.history.model.VkHistoryStats;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.model.VkProperties;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.history.service.VkHistoryService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VkHistoryServiceTest {

    @Mock
    private VkHistoryRepository repository;

    private VkProperties properties;
    private TestableVkHistoryService service;

    @BeforeEach
    void setUp() {
        properties = new VkProperties();
        properties.setGroupId(-1L);
        service = new TestableVkHistoryService(repository, properties);
    }

    @Test
    void refreshWritesFetchedRecordsAndReturnsStats() {
        List<VkImageHistoryRecord> fetched = List.of(
                new VkImageHistoryRecord(1L, "https://example.com/a.jpg", "hash1", Instant.now()),
                new VkImageHistoryRecord(2L, "https://example.com/b.jpg", "hash2", Instant.now())
        );
        service.setFetched(fetched);

        when(repository.save(fetched.get(0))).thenReturn(true);
        when(repository.save(fetched.get(1))).thenReturn(true);
        when(repository.count()).thenReturn(2L);
        when(repository.lastSynced()).thenReturn(Instant.now());

        VkHistoryStats stats = service.refreshFromVk();

        assertThat(stats.totalCount()).isEqualTo(2);
        assertThat(stats.updatedCount()).isEqualTo(2);
        assertThat(stats.lastSynced()).isNotNull();
    }

    @Test
    void recordPublicationAddsMlContextAndSaves() {
        VkImageHistoryRecord record = new VkImageHistoryRecord(
                1L,
                "https://example.com/ml.jpg",
                "hash-ml",
                Instant.now()
        );

        service.recordPublication(record, "PUBLISH", 0.9, "reason");

        ArgumentCaptor<VkImageHistoryRecord> captor = ArgumentCaptor.forClass(VkImageHistoryRecord.class);
        verify(repository).save(captor.capture());
        VkImageHistoryRecord saved = captor.getValue();
        assertThat(saved.getMlDecision()).isEqualTo("PUBLISH");
        assertThat(saved.getMlScore()).isEqualTo(0.9);
        assertThat(saved.getMlReason()).isEqualTo("reason");
    }

    private static class TestableVkHistoryService extends VkHistoryService {

        private List<VkImageHistoryRecord> fetched = List.of();

        protected TestableVkHistoryService(VkHistoryRepository repository, VkProperties vkProperties) {
            super(repository, vkProperties);
        }

        void setFetched(List<VkImageHistoryRecord> fetched) {
            this.fetched = fetched;
        }

        @Override
        protected List<VkImageHistoryRecord> fetchFromVk() {
            return fetched;
        }
    }

    @Test
    void getTrainingEntriesFiltersByFlag() {
        VkImageHistoryRecord allowed = new VkImageHistoryRecord(1L, "url", "hash1", Instant.now());
        allowed.setUseForTraining(true);
        VkImageHistoryRecord denied = new VkImageHistoryRecord(2L, "url", "hash2", Instant.now());
        denied.setUseForTraining(false);
        when(repository.findAll()).thenReturn(List.of(allowed, denied));

        List<VkImageHistoryRecord> training = service.getTrainingEntries();

        assertThat(training).containsExactly(allowed);
    }

    @Test
    void updateUseForTrainingDelegatesToRepository() {
        service.updateUseForTraining(5L, true);

        verify(repository).updateUseForTraining(5L, true);
    }
}
