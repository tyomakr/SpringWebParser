package ru.tyomakr.akcp.library.media.catalog;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.MediaCatalogPort;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.core.content.StorageReference;

public final class InMemoryMediaCatalogAdapter implements MediaCatalogPort {
  private final Map<String, MediaAsset> assetsBySha256 = new HashMap<>();
  private final Map<UUID, MediaAsset> assetsById = new HashMap<>();
  private final Map<SourceKey, SourceOccurrence> occurrencesBySource = new HashMap<>();
  private final Map<UUID, SourceOccurrence> occurrencesById = new HashMap<>();

  @Override
  public synchronized CompletionStage<CatalogRegistration> register(
      MediaAsset candidate,
      SourceOccurrence sourceOccurrence
  ) {
    Objects.requireNonNull(candidate, "candidate is required");
    Objects.requireNonNull(sourceOccurrence, "sourceOccurrence is required");
    if (!candidate.id().equals(sourceOccurrence.mediaAssetId())) {
      throw new IllegalArgumentException("sourceOccurrence must reference candidate");
    }
    MediaAsset existingCandidateId = assetsById.get(candidate.id());
    if (existingCandidateId != null
        && !existingCandidateId.sha256().equals(candidate.sha256())) {
      throw new IllegalStateException(
          "media asset ID is already registered with different content"
      );
    }

    SourceKey sourceKey = SourceKey.from(sourceOccurrence);
    SourceOccurrence existingSource = occurrencesBySource.get(sourceKey);
    if (existingSource != null) {
      MediaAsset existingAsset = requiredAsset(existingSource.mediaAssetId());
      if (!existingAsset.sha256().equals(candidate.sha256())) {
        throw new IllegalStateException("source record already points to different content");
      }
      if (!hasCompatibleMetadata(existingAsset, candidate)) {
        throw new IllegalStateException("content metadata conflicts with canonical media asset");
      }
      return CompletableFuture.completedFuture(
          new CatalogRegistration(existingAsset, existingSource, false, false)
      );
    }

    SourceOccurrence idCollision = occurrencesById.get(sourceOccurrence.id());
    if (idCollision != null) {
      throw new IllegalStateException("source occurrence ID is already registered");
    }

    MediaAsset canonicalAsset = assetsBySha256.get(candidate.sha256());
    boolean assetCreated = canonicalAsset == null;
    if (assetCreated) {
      canonicalAsset = candidate;
      assetsBySha256.put(candidate.sha256(), candidate);
      assetsById.put(candidate.id(), candidate);
    } else if (!hasCompatibleMetadata(canonicalAsset, candidate)) {
      throw new IllegalStateException("content metadata conflicts with canonical media asset");
    }

    SourceOccurrence canonicalSource = withMediaAssetId(
        sourceOccurrence,
        canonicalAsset.id()
    );
    occurrencesBySource.put(sourceKey, canonicalSource);
    occurrencesById.put(canonicalSource.id(), canonicalSource);
    return CompletableFuture.completedFuture(
        new CatalogRegistration(canonicalAsset, canonicalSource, assetCreated, true)
    );
  }

  @Override
  public synchronized CompletionStage<Optional<MediaAsset>> findBySha256(String sha256) {
    return CompletableFuture.completedFuture(
        Optional.ofNullable(assetsBySha256.get(normalizeSha256(sha256)))
    );
  }

  @Override
  public synchronized CompletionStage<List<SourceOccurrence>> findSourceOccurrences(
      UUID mediaAssetId
  ) {
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    return CompletableFuture.completedFuture(
        occurrencesById.values().stream()
            .filter(occurrence -> occurrence.mediaAssetId().equals(mediaAssetId))
            .sorted(Comparator.comparing(SourceOccurrence::discoveredAt)
                .thenComparing(SourceOccurrence::id))
            .toList()
    );
  }

  synchronized int assetCount() {
    return assetsById.size();
  }

  synchronized int sourceOccurrenceCount() {
    return occurrencesById.size();
  }

  private MediaAsset requiredAsset(UUID mediaAssetId) {
    MediaAsset asset = assetsById.get(mediaAssetId);
    if (asset == null) {
      throw new IllegalStateException("catalog invariant violated: media asset is missing");
    }
    return asset;
  }

  private static SourceOccurrence withMediaAssetId(
      SourceOccurrence occurrence,
      UUID mediaAssetId
  ) {
    if (occurrence.mediaAssetId().equals(mediaAssetId)) {
      return occurrence;
    }
    return new SourceOccurrence(
        occurrence.id(),
        mediaAssetId,
        occurrence.platform(),
        occurrence.sourceRecordId(),
        occurrence.sourceConnectionId(),
        occurrence.externalPostId(),
        occurrence.externalMediaId(),
        occurrence.postUrl(),
        occurrence.mediaUrl(),
        occurrence.metadata(),
        occurrence.discoveredAt()
    );
  }

  private static boolean hasCompatibleMetadata(MediaAsset canonical, MediaAsset candidate) {
    return canonical.mimeType().equals(candidate.mimeType())
        && canonical.width() == candidate.width()
        && canonical.height() == candidate.height();
  }

  private static String normalizeSha256(String sha256) {
    Objects.requireNonNull(sha256, "sha256 is required");
    return StorageReference.fromSha256(sha256.toLowerCase(Locale.ROOT)).sha256();
  }

  private record SourceKey(
      SourcePlatform platform,
      String sourceConnectionId,
      String sourceRecordId
  ) {
    private static SourceKey from(SourceOccurrence occurrence) {
      return new SourceKey(
          occurrence.platform(),
          occurrence.sourceConnectionId(),
          occurrence.sourceRecordId()
      );
    }
  }
}
