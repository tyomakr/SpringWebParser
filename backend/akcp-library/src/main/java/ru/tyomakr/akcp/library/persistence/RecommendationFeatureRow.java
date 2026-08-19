package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.UUID;

public record RecommendationFeatureRow(
    UUID id,
    String dataset,
    UUID attachmentId,
    UUID itemId,
    String imageUrl,
    String sha256,
    Long phash,
    String embeddingJson,
    Double textRatio,
    boolean textDominant,
    Instant createdAt,
    Instant updatedAt,
    String analysisVersion,
    String analysisExplanationJson
) {
  public RecommendationFeatureRow(
      UUID id,
      String dataset,
      UUID attachmentId,
      UUID itemId,
      String imageUrl,
      String sha256,
      Long phash,
      String embeddingJson,
      Double textRatio,
      boolean textDominant,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(
        id,
        dataset,
        attachmentId,
        itemId,
        imageUrl,
        sha256,
        phash,
        embeddingJson,
        textRatio,
        textDominant,
        createdAt,
        updatedAt,
        "legacy-url-v1",
        null
    );
  }
}
