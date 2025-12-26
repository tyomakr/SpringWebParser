package ru.aikr.inet.parser.history.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.aikr.inet.parser.history.model.VkSyncProperties;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.model.VkWallSyncStatus;
import ru.aikr.inet.parser.history.repository.VkSyncCheckpointRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class VkWallSyncSchedulerTest {

    @Mock
    private VkWallSyncService syncService;
    @Mock
    private VkSyncCheckpointRepository checkpointRepository;

    private VkWallSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        VkSyncProperties props = new VkSyncProperties();
        props.setEnabled(true);
        props.setPageLimit(0);
        scheduler = new VkWallSyncScheduler(syncService, checkpointRepository, props, registry);
    }

    @Test
    void scheduledSyncRecordsSuccess() {
        when(syncService.syncWall(null, 0))
                .thenReturn(new VkWallSyncReport(1, 1, 1, 0));
        when(checkpointRepository.getSince()).thenReturn(null);

        scheduler.scheduledSync();

        VkWallSyncStatus status = scheduler.getStatus();
        assertThat(status.lastReport().inserted()).isEqualTo(1);
        assertThat(status.lastError()).isNull();
        assertThat(status.running()).isFalse();
    }

    @Test
    void scheduledSyncBacksOffOnRateLimit() {
        when(checkpointRepository.getSince()).thenReturn(null);
        when(syncService.syncWall(null, 0))
                .thenThrow(new VkWallSyncService.RateLimitException(new RuntimeException("limit")));

        scheduler.scheduledSync();

        VkWallSyncStatus status = scheduler.getStatus();
        assertThat(status.lastError()).isEqualTo("Rate limit");
        assertThat(status.backoffUntil()).isAfter(Instant.now());
    }
}
