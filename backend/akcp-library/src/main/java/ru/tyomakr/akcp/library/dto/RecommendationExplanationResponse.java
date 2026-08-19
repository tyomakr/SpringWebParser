package ru.tyomakr.akcp.library.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationExplanationResponse(
    String analysisVersion,
    String rankingVersion,
    double visualWeight,
    double historyWeight,
    double profileWeight,
    double visualScore,
    double historyScore,
    double profileScore,
    double diversityPenalty,
    List<UUID> matchedPublishedExemplars,
    String textPolicy
) {
  public RecommendationExplanationResponse {
    matchedPublishedExemplars = List.copyOf(matchedPublishedExemplars);
  }
}
