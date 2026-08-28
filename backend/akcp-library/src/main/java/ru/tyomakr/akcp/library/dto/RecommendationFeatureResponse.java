package ru.tyomakr.akcp.library.dto;

import java.util.UUID;

public record RecommendationFeatureResponse(
    UUID id,
    String dataset,
    UUID attachmentId,
    String imageUrl,
    boolean textDominant
) {
}
