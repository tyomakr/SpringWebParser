package ru.aikr.inet.parser.mlpublish.model;

import java.util.List;

/**
 * Payload sent to the ML publishing service when asking for preview recommendations.
 */
public record MlRecommendationRequest(List<MlRecommendationCandidate> images) {

    public record MlRecommendationCandidate(String id, String url) {}
}
