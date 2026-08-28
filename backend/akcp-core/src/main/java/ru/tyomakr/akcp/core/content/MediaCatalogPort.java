package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface MediaCatalogPort {
  /**
   * Atomically registers content identity and one source occurrence. Implementations must
   * canonicalize equal SHA-256 content to one media asset and reject a source record that changes
   * its content identity.
   */
  CompletionStage<CatalogRegistration> register(
      MediaAsset candidate,
      SourceOccurrence sourceOccurrence
  );

  CompletionStage<Optional<MediaAsset>> findBySha256(String sha256);

  CompletionStage<List<SourceOccurrence>> findSourceOccurrences(UUID mediaAssetId);
}
