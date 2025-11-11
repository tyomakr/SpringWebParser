package ru.aikr.inet.parser.recommendation.model;

/**
 * Результат по одному изображению от ML-агента.
 */
public record RecommendationResult(
        String id,
        String url,
        double score,
        String reason,
        RecommendationDecision recommendation
) {}
