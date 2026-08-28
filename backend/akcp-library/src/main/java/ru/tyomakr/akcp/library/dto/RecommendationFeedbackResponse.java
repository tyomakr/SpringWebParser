package ru.tyomakr.akcp.library.dto;

import java.time.Instant;
import java.util.UUID;

public record RecommendationFeedbackResponse(
    UUID id,
    String action,
    Instant createdAt,
    UUID runId,
    Integer servedRank,
    String reason
) {
  public RecommendationFeedbackResponse(UUID id, String action, Instant createdAt) {
    this(id, action, createdAt, null, null, null);
  }
}
