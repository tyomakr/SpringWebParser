package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.UUID;

public record RecommendationFeedbackRow(
    UUID id,
    String username,
    UUID referenceAttachmentId,
    UUID recommendedAttachmentId,
    String action,
    String reason,
    UUID servingEventId,
    Integer servedRank,
    String note,
    Instant createdAt
) {
  public RecommendationFeedbackRow(
      UUID id,
      String username,
      UUID referenceAttachmentId,
      UUID recommendedAttachmentId,
      String action,
      String reason,
      Instant createdAt
  ) {
    this(
        id,
        username,
        referenceAttachmentId,
        recommendedAttachmentId,
        action,
        reason,
        null,
        null,
        null,
        createdAt
    );
  }
}
