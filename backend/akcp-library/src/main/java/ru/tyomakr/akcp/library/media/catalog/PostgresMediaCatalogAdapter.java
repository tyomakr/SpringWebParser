package ru.tyomakr.akcp.library.media.catalog;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.MediaCatalogPort;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.core.content.StorageReference;

/**
 * PostgreSQL implementation of the catalog seam. It is intentionally not component-scanned or
 * wired into ingestion yet; runtime activation remains a separate decision together with storage
 * reconciliation.
 */
public final class PostgresMediaCatalogAdapter implements MediaCatalogPort {
  private final DatabaseClient databaseClient;
  private final TransactionalOperator transactions;

  public PostgresMediaCatalogAdapter(
      DatabaseClient databaseClient,
      TransactionalOperator transactions
  ) {
    this.databaseClient = Objects.requireNonNull(databaseClient, "databaseClient is required");
    this.transactions = Objects.requireNonNull(transactions, "transactions is required");
  }

  @Override
  public CompletionStage<CatalogRegistration> register(
      MediaAsset candidate,
      SourceOccurrence sourceOccurrence
  ) {
    Objects.requireNonNull(candidate, "candidate is required");
    Objects.requireNonNull(sourceOccurrence, "sourceOccurrence is required");
    if (!candidate.id().equals(sourceOccurrence.mediaAssetId())) {
      throw new IllegalArgumentException("sourceOccurrence must reference candidate");
    }
    return transactions.transactional(registerInTransaction(candidate, sourceOccurrence))
        .toFuture();
  }

  @Override
  public CompletionStage<Optional<MediaAsset>> findBySha256(String sha256) {
    return findAssetBySha256(normalizeSha256(sha256), false)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .toFuture();
  }

  @Override
  public CompletionStage<List<SourceOccurrence>> findSourceOccurrences(UUID mediaAssetId) {
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    return databaseClient.sql("""
            SELECT id, media_asset_id, platform, source_connection_id, source_record_id,
                   external_post_id, external_media_id, post_url, media_url, metadata,
                   discovered_at
            FROM catalog_source_occurrences
            WHERE media_asset_id = :media_asset_id
            ORDER BY discovered_at, id
            """)
        .bind("media_asset_id", mediaAssetId)
        .map((row, metadata) -> toSourceOccurrence(row))
        .all()
        .collectList()
        .toFuture();
  }

  private Mono<CatalogRegistration> registerInTransaction(
      MediaAsset candidate,
      SourceOccurrence sourceOccurrence
  ) {
    return findAssetById(candidate.id(), true)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(existingById -> {
          validateCandidateId(existingById, candidate);
          return insertAsset(candidate);
        })
        .flatMap(assetCreated -> findAssetById(candidate.id(), true)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(existingById -> {
              validateCandidateId(existingById, candidate);
              return findAssetBySha256(candidate.sha256(), true)
                  .switchIfEmpty(Mono.error(
                      new IllegalStateException("catalog invariant violated: media asset is missing")
                  ))
                  .flatMap(canonical -> {
                    validateCompatibleMetadata(canonical, candidate);
                    return registerSource(
                        canonical,
                        sourceOccurrence,
                        assetCreated && canonical.id().equals(candidate.id())
                    );
                  });
            }));
  }

  private Mono<CatalogRegistration> registerSource(
      MediaAsset canonical,
      SourceOccurrence proposedSource,
      boolean assetCreated
  ) {
    return findSourceByKey(proposedSource, true)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(existingSource -> {
          if (existingSource.isPresent()) {
            return validateExistingSource(canonical, existingSource.orElseThrow())
                .then(Mono.fromSupplier(() -> new CatalogRegistration(
                    canonical,
                    existingSource.orElseThrow(),
                    assetCreated,
                    false
                )));
          }
          return findSourceById(proposedSource.id(), true)
              .flatMap(collision -> Mono.<Boolean>error(
                  new IllegalStateException("source occurrence ID is already registered")
              ))
              .defaultIfEmpty(false)
              .flatMap(ignored -> {
                SourceOccurrence canonicalSource = withMediaAssetId(
                    proposedSource,
                    canonical.id()
                );
                return insertSource(canonicalSource)
                    .flatMap(sourceCreated -> findSourceByKey(canonicalSource, true)
                        .switchIfEmpty(Mono.error(
                            new IllegalStateException(
                                "source occurrence ID is already registered"
                            )
                        ))
                        .flatMap(storedSource -> validateExistingSource(canonical, storedSource)
                            .then(Mono.fromSupplier(() -> new CatalogRegistration(
                                canonical,
                                storedSource,
                                assetCreated,
                                sourceCreated
                            )))));
              });
        });
  }

  private Mono<Void> validateExistingSource(
      MediaAsset candidateCanonical,
      SourceOccurrence existingSource
  ) {
    return findAssetById(existingSource.mediaAssetId(), true)
        .switchIfEmpty(Mono.error(
            new IllegalStateException("catalog invariant violated: media asset is missing")
        ))
        .flatMap(existingAsset -> {
          if (!existingAsset.sha256().equals(candidateCanonical.sha256())) {
            return Mono.error(
                new IllegalStateException("source record already points to different content")
            );
          }
          validateCompatibleMetadata(existingAsset, candidateCanonical);
          return Mono.empty();
        });
  }

  private Mono<Boolean> insertAsset(MediaAsset asset) {
    return databaseClient.sql("""
            INSERT INTO catalog_media_assets (
              id, sha256, mime_type, width, height, storage_reference
            ) VALUES (
              :id, :sha256, :mime_type, :width, :height, :storage_reference
            )
            ON CONFLICT DO NOTHING
            """)
        .bind("id", asset.id())
        .bind("sha256", asset.sha256())
        .bind("mime_type", asset.mimeType())
        .bind("width", asset.width())
        .bind("height", asset.height())
        .bind("storage_reference", asset.storageReference().value())
        .fetch()
        .rowsUpdated()
        .map(count -> count > 0);
  }

  private Mono<Boolean> insertSource(SourceOccurrence source) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO catalog_source_occurrences (
              id, media_asset_id, platform, source_connection_id, source_record_id,
              external_post_id, external_media_id, post_url, media_url, metadata,
              discovered_at
            ) VALUES (
              :id, :media_asset_id, :platform, :source_connection_id, :source_record_id,
              :external_post_id, :external_media_id, :post_url, :media_url, :metadata,
              :discovered_at
            )
            ON CONFLICT DO NOTHING
            """)
        .bind("id", source.id())
        .bind("media_asset_id", source.mediaAssetId())
        .bind("platform", source.platform().name())
        .bind("source_connection_id", databaseConnectionId(source.sourceConnectionId()))
        .bind("source_record_id", source.sourceRecordId())
        .bind("discovered_at", OffsetDateTime.ofInstant(source.discoveredAt(), ZoneOffset.UTC));
    spec = bindNullable(spec, "external_post_id", source.externalPostId(), String.class);
    spec = bindNullable(spec, "external_media_id", source.externalMediaId(), String.class);
    spec = bindNullable(spec, "post_url", source.postUrl(), String.class);
    spec = bindNullable(spec, "media_url", source.mediaUrl(), String.class);
    spec = bindNullable(spec, "metadata", source.metadata(), String.class);
    return spec.fetch().rowsUpdated().map(count -> count > 0);
  }

  private Mono<MediaAsset> findAssetById(UUID id, boolean forUpdate) {
    return databaseClient.sql("""
            SELECT id, sha256, mime_type, width, height, storage_reference
            FROM catalog_media_assets
            WHERE id = :id
            """ + lockClause(forUpdate))
        .bind("id", id)
        .map((row, metadata) -> toMediaAsset(row))
        .one();
  }

  private Mono<MediaAsset> findAssetBySha256(String sha256, boolean forUpdate) {
    return databaseClient.sql("""
            SELECT id, sha256, mime_type, width, height, storage_reference
            FROM catalog_media_assets
            WHERE sha256 = :sha256
            """ + lockClause(forUpdate))
        .bind("sha256", sha256)
        .map((row, metadata) -> toMediaAsset(row))
        .one();
  }

  private Mono<SourceOccurrence> findSourceById(UUID id, boolean forUpdate) {
    return databaseClient.sql("""
            SELECT id, media_asset_id, platform, source_connection_id, source_record_id,
                   external_post_id, external_media_id, post_url, media_url, metadata,
                   discovered_at
            FROM catalog_source_occurrences
            WHERE id = :id
            """ + lockClause(forUpdate))
        .bind("id", id)
        .map((row, metadata) -> toSourceOccurrence(row))
        .one();
  }

  private Mono<SourceOccurrence> findSourceByKey(
      SourceOccurrence source,
      boolean forUpdate
  ) {
    return databaseClient.sql("""
            SELECT id, media_asset_id, platform, source_connection_id, source_record_id,
                   external_post_id, external_media_id, post_url, media_url, metadata,
                   discovered_at
            FROM catalog_source_occurrences
            WHERE platform = :platform
              AND source_connection_id = :source_connection_id
              AND source_record_id = :source_record_id
            """ + lockClause(forUpdate))
        .bind("platform", source.platform().name())
        .bind("source_connection_id", databaseConnectionId(source.sourceConnectionId()))
        .bind("source_record_id", source.sourceRecordId())
        .map((row, metadata) -> toSourceOccurrence(row))
        .one();
  }

  private static MediaAsset toMediaAsset(io.r2dbc.spi.Readable row) {
    return new MediaAsset(
        row.get("id", UUID.class),
        row.get("sha256", String.class),
        row.get("mime_type", String.class),
        requiredInteger(row, "width"),
        requiredInteger(row, "height"),
        new StorageReference(row.get("storage_reference", String.class))
    );
  }

  private static SourceOccurrence toSourceOccurrence(io.r2dbc.spi.Readable row) {
    OffsetDateTime discoveredAt = row.get("discovered_at", OffsetDateTime.class);
    if (discoveredAt == null) {
      throw new IllegalStateException("catalog invariant violated: discovered_at is missing");
    }
    return new SourceOccurrence(
        row.get("id", UUID.class),
        row.get("media_asset_id", UUID.class),
        SourcePlatform.valueOf(row.get("platform", String.class)),
        row.get("source_record_id", String.class),
        domainConnectionId(row.get("source_connection_id", String.class)),
        row.get("external_post_id", String.class),
        row.get("external_media_id", String.class),
        row.get("post_url", String.class),
        row.get("media_url", String.class),
        row.get("metadata", String.class),
        discoveredAt.toInstant()
    );
  }

  private static int requiredInteger(io.r2dbc.spi.Readable row, String column) {
    Integer value = row.get(column, Integer.class);
    if (value == null) {
      throw new IllegalStateException("catalog invariant violated: " + column + " is missing");
    }
    return value;
  }

  private static void validateCandidateId(Optional<MediaAsset> existing, MediaAsset candidate) {
    if (existing.isPresent() && !existing.orElseThrow().sha256().equals(candidate.sha256())) {
      throw new IllegalStateException("media asset ID is already registered with different content");
    }
  }

  private static void validateCompatibleMetadata(MediaAsset canonical, MediaAsset candidate) {
    if (!canonical.mimeType().equals(candidate.mimeType())
        || canonical.width() != candidate.width()
        || canonical.height() != candidate.height()) {
      throw new IllegalStateException("content metadata conflicts with canonical media asset");
    }
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

  private static String normalizeSha256(String sha256) {
    Objects.requireNonNull(sha256, "sha256 is required");
    return StorageReference.fromSha256(sha256.toLowerCase(Locale.ROOT)).sha256();
  }

  private static String databaseConnectionId(String sourceConnectionId) {
    return sourceConnectionId == null ? "" : sourceConnectionId;
  }

  private static String domainConnectionId(String sourceConnectionId) {
    return sourceConnectionId == null || sourceConnectionId.isEmpty()
        ? null
        : sourceConnectionId;
  }

  private static String lockClause(boolean forUpdate) {
    return forUpdate ? " FOR UPDATE" : "";
  }

  private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
      DatabaseClient.GenericExecuteSpec spec,
      String name,
      T value,
      Class<T> type
  ) {
    return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
  }
}
