package ru.tyomakr.akcp.core.content;

import java.util.Objects;
import java.util.UUID;

public record ChannelProfile(
    UUID id,
    PublicationPlatform platform,
    String externalChannelId,
    String displayName
) {
  public ChannelProfile {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(platform, "platform is required");
    Objects.requireNonNull(externalChannelId, "externalChannelId is required");
    Objects.requireNonNull(displayName, "displayName is required");
    if (externalChannelId.isBlank()) {
      throw new IllegalArgumentException("externalChannelId must not be blank");
    }
    if (displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
  }
}
