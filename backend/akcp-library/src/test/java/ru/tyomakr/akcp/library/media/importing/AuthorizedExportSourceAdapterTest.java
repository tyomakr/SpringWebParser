package ru.tyomakr.akcp.library.media.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.SourceImportBatch;
import ru.tyomakr.akcp.core.content.SourceImportRecord;
import ru.tyomakr.akcp.core.content.SourceImportResult;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.library.media.catalog.InMemoryMediaCatalogAdapter;
import ru.tyomakr.akcp.library.media.storage.InMemoryStorageAdapter;

class AuthorizedExportSourceAdapterTest {
  private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

  @Test
  void sourceAdapterOnlyNormalizesMatchingPlatform() {
    AuthorizedExportSourceAdapter adapter = new AuthorizedExportSourceAdapter(SourcePlatform.VK);
    SourceImportBatch batch = batch("vk:group:1", "post-1", new byte[] {10, 20, 30});

    assertEquals(batch, await(adapter.normalize(batch)));

    SourceImportBatch telegram = new SourceImportBatch(
        UUID.randomUUID(),
        SourcePlatform.TELEGRAM,
        "channel-1",
        List.of(record("message-1", new byte[] {1}))
    );
    assertThrows(CompletionException.class, () -> await(adapter.normalize(telegram)));
  }

  @Test
  void repeatedAuthorizedExportIsIdempotentAndKeepsSourceOccurrence() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    SourceIngestionCoordinator coordinator = coordinator(storage, catalog);
    SourceImportBatch batch = batch("vk:group:1", "post-1", new byte[] {10, 20, 30});

    SourceImportResult first = await(coordinator.ingest(batch));
    SourceImportResult repeat = await(coordinator.ingest(batch));

    assertEquals(1, first.imported());
    assertEquals(0, first.reused());
    assertEquals(0, repeat.imported());
    assertEquals(1, repeat.reused());
    assertEquals(first.mediaAssetIds(), repeat.mediaAssetIds());
    assertEquals(1, await(catalog.findSourceOccurrences(first.mediaAssetIds().get(0))).size());
    assertEquals(1, storage.objectCount());
  }

  @Test
  void sameBytesFromAnotherSourceRecordReusesAssetButAddsOccurrence() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    SourceIngestionCoordinator coordinator = coordinator(storage, catalog);

    SourceImportResult first = await(
        coordinator.ingest(batch("vk:group:1", "post-1", new byte[] {1, 2}))
    );
    SourceImportResult second = await(
        coordinator.ingest(batch("vk:group:1", "post-2", new byte[] {1, 2}))
    );

    assertEquals(1, first.imported());
    assertEquals(1, second.imported());
    assertEquals(first.mediaAssetIds(), second.mediaAssetIds());
    assertEquals(2, await(catalog.findSourceOccurrences(first.mediaAssetIds().get(0))).size());
  }

  @Test
  void changedContentForSameSourceRecordIsRejectedWithoutNewOccurrence() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    SourceIngestionCoordinator coordinator = coordinator(storage, catalog);

    SourceImportResult first = await(
        coordinator.ingest(batch("vk:group:1", "post-1", new byte[] {1}))
    );
    assertThrows(CompletionException.class, () -> await(
        coordinator.ingest(batch("vk:group:1", "post-1", new byte[] {2}))
    ));
    assertEquals(1, await(catalog.findSourceOccurrences(first.mediaAssetIds().get(0))).size());
  }

  private SourceIngestionCoordinator coordinator(
      InMemoryStorageAdapter storage,
      InMemoryMediaCatalogAdapter catalog
  ) {
    return new SourceIngestionCoordinator(
        new AuthorizedExportSourceAdapter(SourcePlatform.VK),
        storage,
        catalog
    );
  }

  private SourceImportBatch batch(String connection, String id, byte[] bytes) {
    return new SourceImportBatch(
        UUID.nameUUIDFromBytes((connection + ":" + id).getBytes()),
        SourcePlatform.VK,
        connection,
        List.of(record(id, bytes))
    );
  }

  private SourceImportRecord record(String id, byte[] bytes) {
    return new SourceImportRecord(
        id,
        id,
        "media-" + id,
        "https://fixture.invalid/posts/" + id,
        "https://fixture.invalid/media/" + id,
        "image/jpeg",
        10,
        10,
        bytes,
        "fixture=" + Base64.getEncoder().encodeToString(bytes),
        NOW
    );
  }

  private static <T> T await(java.util.concurrent.CompletionStage<T> stage) {
    return stage.toCompletableFuture().join();
  }
}
