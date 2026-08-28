package ru.tyomakr.akcp.library.media.importing;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import ru.tyomakr.akcp.core.content.SourceAdapter;
import ru.tyomakr.akcp.core.content.SourceImportBatch;
import ru.tyomakr.akcp.core.content.SourcePlatform;

/**
 * Source-only boundary for a bounded authorized export. It never writes the catalog, fetches URLs,
 * calls a platform API, reads credentials or publishes.
 */
public final class AuthorizedExportSourceAdapter implements SourceAdapter {
  private final SourcePlatform platform;

  public AuthorizedExportSourceAdapter(SourcePlatform platform) {
    this.platform = Objects.requireNonNull(platform, "platform is required");
  }

  @Override
  public SourcePlatform platform() {
    return platform;
  }

  @Override
  public CompletionStage<SourceImportBatch> normalize(SourceImportBatch batch) {
    Objects.requireNonNull(batch, "batch is required");
    if (batch.platform() != platform) {
      return CompletableFuture.failedFuture(new IllegalArgumentException(
          "batch platform does not match source adapter: " + batch.platform()
      ));
    }
    return CompletableFuture.completedFuture(batch);
  }
}
