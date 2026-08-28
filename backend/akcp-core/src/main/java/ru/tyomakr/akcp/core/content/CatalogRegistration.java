package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record CatalogRegistration(
    MediaAsset mediaAsset,
    SourceOccurrence sourceOccurrence,
    boolean mediaAssetCreated,
    boolean sourceOccurrenceCreated
) {
  public CatalogRegistration {
    Objects.requireNonNull(mediaAsset, "mediaAsset is required");
    Objects.requireNonNull(sourceOccurrence, "sourceOccurrence is required");
    if (!mediaAsset.id().equals(sourceOccurrence.mediaAssetId())) {
      throw new IllegalArgumentException("sourceOccurrence must reference mediaAsset");
    }
  }
}
