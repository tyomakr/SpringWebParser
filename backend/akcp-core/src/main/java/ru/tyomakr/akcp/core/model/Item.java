package ru.tyomakr.akcp.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Item(
    UUID id,
    String title,
    String content,
    SourceRef source,
    List<Attachment> attachments,
    Set<Tag> tags,
    Instant createdAt,
    Instant updatedAt
) {
}
