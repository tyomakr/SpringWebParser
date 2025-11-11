package ru.aikr.inet.parser.mlpublish;

import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;

/**
 * Domain model for a single recommendation coming from the ML publishing service.
 */
public record MlRecommendation(
        String id,
        String url,
        double score,
        String reason,
        RecommendationDecision decision
) {}
