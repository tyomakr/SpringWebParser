package ru.aikr.inet.parser.history.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.aikr.inet.parser.history.model.VkSyncProperties;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.model.VkWallSyncStatus;
import ru.aikr.inet.parser.history.repository.VkSyncCheckpointRepository;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class VkWallSyncScheduler {

    private final VkWallSyncService syncService;
    private final VkSyncCheckpointRepository checkpointRepository;
    private final VkSyncProperties syncProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Counter successCounter;
    private final Counter failureCounter;
    private volatile Instant backoffUntil = Instant.EPOCH;
    private volatile Instant lastRun;
    private volatile VkWallSyncReport lastReport;
    private volatile String lastError;
    private volatile Instant lastSince;

    public VkWallSyncScheduler(VkWallSyncService syncService,
                               VkSyncCheckpointRepository checkpointRepository,
                               VkSyncProperties syncProperties,
                               MeterRegistry meterRegistry) {
        this.syncService = syncService;
        this.checkpointRepository = checkpointRepository;
        this.syncProperties = syncProperties;
        this.successCounter = meterRegistry.counter("vk.wall.sync.scheduled.success");
        this.failureCounter = meterRegistry.counter("vk.wall.sync.scheduled.failure");
    }

    @Scheduled(cron = "#{@vkSyncProperties.cron}")
    public void scheduledSync() {
        if (!syncProperties.isEnabled() || Instant.now().isBefore(backoffUntil)
                || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant since = checkpointRepository.getSince();
            lastSince = since;
            VkWallSyncReport report = syncService.syncWall(since, syncProperties.getPageLimit());
            lastReport = report;
            lastRun = Instant.now();
            lastError = null;
            successCounter.increment();
            checkpointRepository.saveSince(lastRun);
        } catch (VkWallSyncService.RateLimitException ex) {
            failureCounter.increment();
            lastError = "Rate limit";
            backoffUntil = Instant.now().plus(syncProperties.getRateLimit());
            log.warn("VK rate limit hit: {}", ex.getMessage());
        } catch (Exception ex) {
            failureCounter.increment();
            lastError = ex.getMessage();
            log.error("Scheduled VK sync failed", ex);
        } finally {
            running.set(false);
        }
    }

    public boolean triggerManualSync(Instant since, int pagesLimit) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        lastSince = since;
        lastError = null;
        Mono.fromCallable(() -> syncService.syncWall(since, pagesLimit))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(report -> {
                    lastReport = report;
                    lastRun = Instant.now();
                })
                .doOnError(ex -> {
                    lastError = ex.getMessage();
                    log.error("Manual VK sync failed", ex);
                })
                .doFinally(signal -> running.set(false))
                .subscribe();
        return true;
    }

    public VkWallSyncStatus getStatus() {
        return new VkWallSyncStatus(
                running.get(),
                lastRun,
                lastReport,
                lastError,
                backoffUntil,
                lastSince,
                syncProperties.getRateLimit()
        );
    }
}
