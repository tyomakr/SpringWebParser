package ru.tyomakr.akcp.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SavedSelectionCleanupJob {
  private static final Logger log = LoggerFactory.getLogger(SavedSelectionCleanupJob.class);

  private final SavedSelectionService service;

  public SavedSelectionCleanupJob(SavedSelectionService service) {
    this.service = service;
  }

  @Scheduled(
      initialDelayString = "#{@selectionsProperties.cleanupIntervalMs}",
      fixedDelayString = "#{@selectionsProperties.cleanupIntervalMs}"
  )
  public void runTick() {
    service.cleanupExpired()
        .doOnError(error -> log.warn("Saved selections cleanup failed", error))
        .subscribe();
  }
}
