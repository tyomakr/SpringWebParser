package ru.tyomakr.akcp.library.media.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.MediaCatalogPort;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.StoragePort;
import ru.tyomakr.akcp.core.content.StoredMedia;

/**
 * Coordinates storage before atomic catalog registration. A rejected catalog registration can
 * leave an unreferenced immutable storage object; runtime wiring therefore requires an explicit
 * scrub/garbage-collection or two-phase registration policy.
 */
public final class MediaCatalogImportCoordinator {
  private final StoragePort storage;
  private final MediaCatalogPort catalog;

  public MediaCatalogImportCoordinator(StoragePort storage, MediaCatalogPort catalog) {
    this.storage = Objects.requireNonNull(storage, "storage is required");
    this.catalog = Objects.requireNonNull(catalog, "catalog is required");
  }

  public CompletionStage<CatalogRegistration> importMedia(
      InputStream content,
      String mimeType,
      int width,
      int height,
      SourceOccurrence sourceOccurrence
  ) throws IOException {
    Objects.requireNonNull(content, "content is required");
    Objects.requireNonNull(mimeType, "mimeType is required");
    Objects.requireNonNull(sourceOccurrence, "sourceOccurrence is required");
    if (mimeType.isBlank()) {
      throw new IllegalArgumentException("mimeType must not be blank");
    }
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("width and height must be positive");
    }

    StoredMedia stored = storage.store(content);
    MediaAsset candidate = new MediaAsset(
        sourceOccurrence.mediaAssetId(),
        stored.sha256(),
        mimeType,
        width,
        height,
        stored.reference()
    );
    return catalog.register(candidate, sourceOccurrence);
  }
}
