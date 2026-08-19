package ru.tyomakr.akcp.library.dto;

import java.util.UUID;

public record RecommendationCandidateResponse(
    UUID attachmentId,
    UUID itemId,
    String imageUrl,
    double score,
    double visualScore,
    double historyScore,
    String reason,
    int rank,
    double diversityPenalty,
    RecommendationExplanationResponse explanation
) {
  public RecommendationCandidateResponse(
      UUID attachmentId,
      UUID itemId,
      String imageUrl,
      double score,
      double visualScore,
      double historyScore,
      String reason
  ) {
    this(attachmentId, itemId, imageUrl, score, visualScore, historyScore, reason, 0, 0.0d, null);
  }
}
