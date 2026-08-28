package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Batch outcome. imported/reused count newly-created/reused source occurrences; mediaAssetIds are
 * the canonical asset IDs corresponding to each attempted source record.
 */
public record SourceImportResult(
    UUID batchId,
    int attempted,
    int imported,
    int reused,
    List<UUID> mediaAssetIds
) {
  public SourceImportResult {
    Objects.requireNonNull(batchId, "batchId is required");
    Objects.requireNonNull(mediaAssetIds, "mediaAssetIds are required");
    if (attempted < 0 || imported < 0 || reused < 0 || imported + reused != attempted) {
      throw new IllegalArgumentException("import counters are inconsistent");
    }
    if (mediaAssetIds.size() != attempted) {
      throw new IllegalArgumentException("one media asset ID is required per attempted record");
    }
    if (mediaAssetIds.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("mediaAssetIds must not contain nulls");
    }
    mediaAssetIds = List.copyOf(mediaAssetIds);
  }
}
