package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;

/** One immutable record from an authorized source export or fixture. */
public record SourceImportRecord(
    String sourceRecordId,
    String externalPostId,
    String externalMediaId,
    String postUrl,
    String mediaUrl,
    String mimeType,
    int width,
    int height,
    byte[] content,
    String metadata,
    Instant discoveredAt
) {
  public SourceImportRecord {
    sourceRecordId = requireNonBlank(sourceRecordId, "sourceRecordId");
    mimeType = requireNonBlank(mimeType, "mimeType");
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("width and height must be positive");
    }
    Objects.requireNonNull(content, "content is required");
    if (content.length == 0) {
      throw new IllegalArgumentException("content must not be empty");
    }
    discoveredAt = Objects.requireNonNull(discoveredAt, "discoveredAt is required");
    content = content.clone();
    externalPostId = normalizeOptional(externalPostId);
    externalMediaId = normalizeOptional(externalMediaId);
    postUrl = normalizeOptional(postUrl);
    mediaUrl = normalizeOptional(mediaUrl);
    metadata = normalizeOptional(metadata);
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  private static String requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name + " is required");
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
