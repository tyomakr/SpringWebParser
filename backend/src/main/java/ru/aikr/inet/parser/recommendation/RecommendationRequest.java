package ru.aikr.inet.parser.recommendation;

import java.util.List;

public record RecommendationRequest(List<RecommendationInput> images) {
    public record RecommendationInput(String id, String url) {}
}
