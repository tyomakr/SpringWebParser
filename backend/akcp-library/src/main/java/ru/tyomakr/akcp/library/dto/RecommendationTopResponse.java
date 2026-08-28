package ru.tyomakr.akcp.library.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationTopResponse(
    UUID referenceAttachmentId,
    int requestedLimit,
    int returnedCount,
    List<RecommendationCandidateResponse> candidates,
    UUID runId,
    String rankingVersion,
    List<RecommendationExclusionResponse> exclusions
) {
  public RecommendationTopResponse {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
    exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
  }

  public RecommendationTopResponse(
      UUID referenceAttachmentId,
      int requestedLimit,
      int returnedCount,
      List<RecommendationCandidateResponse> candidates,
      UUID runId,
      String rankingVersion
  ) {
    this(referenceAttachmentId, requestedLimit, returnedCount, candidates, runId, rankingVersion, List.of());
  }

  public RecommendationTopResponse(
      UUID referenceAttachmentId,
      int requestedLimit,
      int returnedCount,
      List<RecommendationCandidateResponse> candidates
  ) {
    this(referenceAttachmentId, requestedLimit, returnedCount, candidates, null, null, List.of());
  }
}
