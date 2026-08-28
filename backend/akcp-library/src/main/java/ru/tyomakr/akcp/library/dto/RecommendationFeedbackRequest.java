package ru.tyomakr.akcp.library.dto;

import java.util.UUID;

public record RecommendationFeedbackRequest(
    UUID referenceAttachmentId,
    UUID recommendedAttachmentId,
    String action,
    String reason,
    UUID runId,
    Integer servedRank,
    String note
) {
  public RecommendationFeedbackRequest(
      UUID referenceAttachmentId,
      UUID recommendedAttachmentId,
      String action,
      String reason
  ) {
    this(referenceAttachmentId, recommendedAttachmentId, action, reason, null, null, null);
  }
}
