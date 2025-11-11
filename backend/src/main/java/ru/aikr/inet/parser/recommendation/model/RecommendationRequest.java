package ru.aikr.inet.parser.recommendation.model;

import java.util.List;

public record RecommendationRequest(List<RecommendationInput> images) {
    public record RecommendationInput(String id, String url) {}
}
