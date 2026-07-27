package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
  }
}
