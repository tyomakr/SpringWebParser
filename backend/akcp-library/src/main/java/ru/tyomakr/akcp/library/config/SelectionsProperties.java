package ru.tyomakr.akcp.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "akcp.selections")
public class SelectionsProperties {
  private long ttlHours = 48;
  private long cleanupIntervalMs = 3600000;

  public long getTtlHours() {
    return ttlHours;
  }

  public void setTtlHours(long ttlHours) {
    if (ttlHours > 0) {
      this.ttlHours = ttlHours;
    }
  }

  public long getCleanupIntervalMs() {
    return cleanupIntervalMs;
  }

  public void setCleanupIntervalMs(long cleanupIntervalMs) {
    if (cleanupIntervalMs > 0) {
      this.cleanupIntervalMs = cleanupIntervalMs;
    }
  }
}
