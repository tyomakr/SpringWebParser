package ru.tyomakr.akcp.library.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ItemResponse(
    UUID id,
    String title,
    String content,
    String sourceType,
    String sourceUrl,
    List<AttachmentResponse> attachments,
    Set<TagResponse> tags,
    Instant createdAt,
    Instant updatedAt
) {
}
