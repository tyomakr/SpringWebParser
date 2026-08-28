package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One observation of media at a source. sourceRecordId is stable within
 * (platform, sourceConnectionId); adapters without a connection ID must provide a globally scoped
 * sourceRecordId for their platform.
 */
public record SourceOccurrence(
    UUID id,
    UUID mediaAssetId,
    SourcePlatform platform,
    String sourceRecordId,
    String sourceConnectionId,
    String externalPostId,
    String externalMediaId,
    String postUrl,
    String mediaUrl,
    String metadata,
    Instant discoveredAt
) {
  public SourceOccurrence {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(platform, "platform is required");
    Objects.requireNonNull(sourceRecordId, "sourceRecordId is required");
    Objects.requireNonNull(discoveredAt, "discoveredAt is required");
    if (sourceRecordId.isBlank()) {
      throw new IllegalArgumentException("sourceRecordId must not be blank");
    }
    sourceRecordId = sourceRecordId.trim();
    sourceConnectionId = normalizeOptional(sourceConnectionId);
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
