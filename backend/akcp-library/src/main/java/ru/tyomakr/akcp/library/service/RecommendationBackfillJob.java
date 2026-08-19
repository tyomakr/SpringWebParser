package ru.tyomakr.akcp.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tyomakr.akcp.library.config.RecommendationProperties;

@Component
public class RecommendationBackfillJob {
  private static final Logger log = LoggerFactory.getLogger(RecommendationBackfillJob.class);

  private final RecommendationProperties properties;
  private final RecommendationService recommendationService;

  public RecommendationBackfillJob(RecommendationProperties properties, RecommendationService recommendationService) {
    this.properties = properties;
    this.recommendationService = recommendationService;
  }

  @Scheduled(fixedDelayString = "#{@recommendationProperties.backfillIntervalMs}")
  public void runTick() {
    if (!properties.isBackfillEnabled()) {
      return;
    }
    recommendationService.backfillFeatures(null)
        .doOnSuccess(response -> log.info(
            "Recommendation backfill tick completed: scanned={} upserted={} durationMs={}",
            response.scanned(),
            response.upserted(),
            response.durationMs()
        ))
        .doOnError(error -> log.warn("Recommendation backfill tick failed", error))
        .subscribe();
  }
}
