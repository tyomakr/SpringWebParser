package ru.tyomakr.akcp.library.media.importing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.MediaCatalogPort;
import ru.tyomakr.akcp.core.content.SourceAdapter;
import ru.tyomakr.akcp.core.content.SourceImportBatch;
import ru.tyomakr.akcp.core.content.SourceImportRecord;
import ru.tyomakr.akcp.core.content.SourceImportResult;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.StoragePort;
import ru.tyomakr.akcp.core.content.StoredMedia;

/**
 * Ingestion application service. It owns storage/catalog matching and idempotency after a source
 * adapter emits a normalized batch. Batches are replayable from the beginning; no cursor is
 * persisted by this bounded PE4 slice yet.
 */
public final class SourceIngestionCoordinator {
  private final SourceAdapter sourceAdapter;
  private final StoragePort storage;
  private final MediaCatalogPort catalog;

  public SourceIngestionCoordinator(
      SourceAdapter sourceAdapter,
      StoragePort storage,
      MediaCatalogPort catalog
  ) {
    this.sourceAdapter = Objects.requireNonNull(sourceAdapter, "sourceAdapter is required");
    this.storage = Objects.requireNonNull(storage, "storage is required");
    this.catalog = Objects.requireNonNull(catalog, "catalog is required");
  }

  public CompletionStage<SourceImportResult> ingest(SourceImportBatch batch) {
    return sourceAdapter.normalize(batch).thenCompose(this::ingestNormalized);
  }

  private CompletionStage<SourceImportResult> ingestNormalized(SourceImportBatch batch) {
    CompletableFuture<ImportState> chain = CompletableFuture.completedFuture(new ImportState());
    for (SourceImportRecord record : batch.records()) {
      chain = chain.thenCompose(state -> importRecord(batch, record)
          .thenApply(state::add));
    }
    return chain.thenApply(state -> new SourceImportResult(
        batch.batchId(),
        state.attempted,
        state.imported,
        state.reused,
        state.mediaAssetIds
    ));
  }

  private CompletionStage<Observation> importRecord(
      SourceImportBatch batch,
      SourceImportRecord record
  ) {
    final StoredMedia stored;
    try {
      stored = storage.store(new ByteArrayInputStream(record.content()));
    } catch (IOException | RuntimeException ex) {
      return failed(ex);
    }

    String sha256 = stored.sha256();
    UUID assetId = UUID.nameUUIDFromBytes(
        ("asset:" + sha256).getBytes(StandardCharsets.UTF_8)
    );
    UUID occurrenceId = UUID.nameUUIDFromBytes(
        ("occurrence:" + batch.platform() + ":" + nullToEmpty(batch.sourceConnectionId())
            + ":" + record.sourceRecordId()).getBytes(StandardCharsets.UTF_8)
    );
    MediaAsset candidate = new MediaAsset(
        assetId,
        sha256,
        record.mimeType(),
        record.width(),
        record.height(),
        stored.reference()
    );
    SourceOccurrence occurrence = new SourceOccurrence(
        occurrenceId,
        assetId,
        batch.platform(),
        record.sourceRecordId(),
        batch.sourceConnectionId(),
        record.externalPostId(),
        record.externalMediaId(),
        record.postUrl(),
        record.mediaUrl(),
        record.metadata(),
        record.discoveredAt()
    );
    return catalog.register(candidate, occurrence)
        .thenApply(registration -> new Observation(
            registration.mediaAsset().id(),
            registration.mediaAssetCreated() || registration.sourceOccurrenceCreated()
        ));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static <T> CompletionStage<T> failed(Throwable error) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(error);
    return future;
  }

  private static final class ImportState {
    private int attempted;
    private int imported;
    private int reused;
    private final List<UUID> mediaAssetIds = new ArrayList<>();

    private ImportState add(Observation observation) {
      attempted++;
      if (observation.created()) {
        imported++;
      } else {
        reused++;
      }
      mediaAssetIds.add(observation.mediaAssetId());
      return this;
    }
  }

  private record Observation(UUID mediaAssetId, boolean created) {
  }
}
