package ru.tyomakr.akcp.library.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecommendationAttachmentSourceRow(
    UUID attachmentId,
    UUID itemId,
    String imageUrl,
    String sourceType,
    Instant itemCreatedAt,
    List<String> tags,
    String analysisSha256,
    Long analysisPhash,
    String analysisEmbeddingJson,
    Double analysisTextRatio,
    Boolean analysisTextDominant,
    String analysisVersion,
    String analysisExplanationJson
) {
}
