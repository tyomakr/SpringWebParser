package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublicationOccurrence(
    UUID id,
    UUID mediaAssetId,
    UUID channelProfileId,
    String externalPublicationId,
    Instant publishedAt
) {
  public PublicationOccurrence {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(channelProfileId, "channelProfileId is required");
    Objects.requireNonNull(externalPublicationId, "externalPublicationId is required");
    Objects.requireNonNull(publishedAt, "publishedAt is required");
    if (externalPublicationId.isBlank()) {
      throw new IllegalArgumentException("externalPublicationId must not be blank");
    }
  }
}
