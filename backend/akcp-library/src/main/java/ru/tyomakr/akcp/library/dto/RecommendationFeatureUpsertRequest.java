package ru.tyomakr.akcp.library.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationFeatureUpsertRequest(
    String dataset,
    UUID attachmentId,
    String imageUrl,
    String sha256,
    Long phash,
    List<Double> embedding,
    Double textRatio,
    Boolean textDominant
) {
}
