package ru.aikr.inet.parser.recommendation;

import java.util.List;

/**
 * Ответ ML-сервиса с сырыми данными для каждой рекомендации.
 */
public record RecommendationResponse(List<RecommendationItem> recommendations) {
    public record RecommendationItem(
            String id,
            String url,
            double score,
            String reason,
            String recommendation
    ) {}
}
