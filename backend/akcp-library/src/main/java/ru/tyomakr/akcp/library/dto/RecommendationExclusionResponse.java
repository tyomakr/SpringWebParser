package ru.tyomakr.akcp.library.dto;

import java.util.UUID;

public record RecommendationExclusionResponse(
    UUID attachmentId,
    String rule,
    String analysisVersion,
    String evidence,
    Double threshold
) {
}
