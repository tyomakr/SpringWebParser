package ru.tyomakr.akcp.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "akcp.recommendations")
public class RecommendationProperties {
  private boolean backfillEnabled = false;
  private long backfillIntervalMs = 300000;
  private int backfillBatchSize = 200;
  private String experimentGroup = "profile_v1";
  private double visualWeight = 0.75d;
  private double historyWeight = 0.15d;
  private double profileWeight = 0.10d;
  private double textDominantThreshold = 0.65d;

  public boolean isBackfillEnabled() {
    return backfillEnabled;
  }

  public void setBackfillEnabled(boolean backfillEnabled) {
    this.backfillEnabled = backfillEnabled;
  }

  public long getBackfillIntervalMs() {
    return backfillIntervalMs;
  }

  public void setBackfillIntervalMs(long backfillIntervalMs) {
    if (backfillIntervalMs > 0) {
      this.backfillIntervalMs = backfillIntervalMs;
    }
  }

  public int getBackfillBatchSize() {
    return backfillBatchSize;
  }

  public void setBackfillBatchSize(int backfillBatchSize) {
    if (backfillBatchSize > 0) {
      this.backfillBatchSize = backfillBatchSize;
    }
  }

  public String getExperimentGroup() {
    return experimentGroup;
  }

  public void setExperimentGroup(String experimentGroup) {
    if (experimentGroup != null && !experimentGroup.isBlank()) {
      this.experimentGroup = experimentGroup.trim();
    }
  }

  public double getVisualWeight() {
    return visualWeight;
  }

  public void setVisualWeight(double visualWeight) {
    this.visualWeight = clamp01(visualWeight, this.visualWeight);
  }

  public double getHistoryWeight() {
    return historyWeight;
  }

  public void setHistoryWeight(double historyWeight) {
    this.historyWeight = clamp01(historyWeight, this.historyWeight);
  }

  public double getProfileWeight() {
    return profileWeight;
  }

  public void setProfileWeight(double profileWeight) {
    this.profileWeight = clamp01(profileWeight, this.profileWeight);
  }

  public double getTextDominantThreshold() {
    return textDominantThreshold;
  }

  public void setTextDominantThreshold(double textDominantThreshold) {
    this.textDominantThreshold = clamp01(textDominantThreshold, this.textDominantThreshold);
  }

  private double clamp01(double value, double fallback) {
    if (value < 0.0d || value > 1.0d) {
      return fallback;
    }
    return value;
  }
}
