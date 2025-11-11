package ru.aikr.inet.parser.mlpublish.model;

import java.util.List;

/**
 * Response received from the ML publishing service.
 */
public record MlRecommendationResponse(List<MlRecommendationItem> recommendations) {

    public record MlRecommendationItem(
            String id,
            String url,
            double score,
            String reason,
            String decision
    ) {}
}
