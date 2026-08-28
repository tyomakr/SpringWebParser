package ru.tyomakr.akcp.library.media.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.library.media.storage.InMemoryStorageAdapter;

class MediaCatalogImportCoordinatorTest {
  private static final byte[] IMAGE_BYTES = "fixture-image".getBytes(StandardCharsets.UTF_8);
  private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

  @Test
  void importsSameBytesFromTwoSourcesAsOneAsset() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaCatalogImportCoordinator coordinator = new MediaCatalogImportCoordinator(
        storage,
        catalog
    );

    CatalogRegistration vk = await(coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        "image/jpeg",
        1280,
        720,
        source(UUID.randomUUID(), SourcePlatform.VK, "vk:photo:10")
    ));
    CatalogRegistration web = await(coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        "image/jpeg",
        1280,
        720,
        source(UUID.randomUUID(), SourcePlatform.WEB, "web:image:10")
    ));

    assertThat(vk.mediaAssetCreated()).isTrue();
    assertThat(web.mediaAssetCreated()).isFalse();
    assertThat(web.mediaAsset().id()).isEqualTo(vk.mediaAsset().id());
    assertThat(await(catalog.findSourceOccurrences(vk.mediaAsset().id()))).hasSize(2);
    assertThat(storage.objectCount()).isEqualTo(1);
  }

  @Test
  void repeatedImportOfOneSourceIsIdempotent() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaCatalogImportCoordinator coordinator = new MediaCatalogImportCoordinator(
        storage,
        catalog
    );
    SourceOccurrence source = source(
        UUID.randomUUID(),
        SourcePlatform.VK,
        "vk:photo:11"
    );

    CatalogRegistration first = await(coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        "image/jpeg",
        1280,
        720,
        source
    ));
    CatalogRegistration repeated = await(coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        "image/jpeg",
        1280,
        720,
        source
    ));

    assertThat(first.sourceOccurrenceCreated()).isTrue();
    assertThat(repeated.sourceOccurrenceCreated()).isFalse();
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(storage.objectCount()).isEqualTo(1);
  }

  @Test
  void rejectsInvalidMetadataBeforeWritingContent() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    MediaCatalogImportCoordinator coordinator = new MediaCatalogImportCoordinator(
        storage,
        new InMemoryMediaCatalogAdapter()
    );

    assertThatThrownBy(() -> coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        " ",
        1280,
        720,
        source(UUID.randomUUID(), SourcePlatform.WEB, "web:image:12")
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("mimeType must not be blank");
    assertThat(storage.objectCount()).isZero();
  }

  @Test
  void catalogRejectionLeavesOnlyAnUnreferencedImmutableObject() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaCatalogImportCoordinator coordinator = new MediaCatalogImportCoordinator(
        storage,
        catalog
    );
    SourceOccurrence source = source(
        UUID.randomUUID(),
        SourcePlatform.VK,
        "vk:photo:13"
    );
    CatalogRegistration accepted = await(coordinator.importMedia(
        new ByteArrayInputStream(IMAGE_BYTES),
        "image/jpeg",
        1280,
        720,
        source
    ));
    byte[] changedBytes = "changed-image".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> await(coordinator.importMedia(
        new ByteArrayInputStream(changedBytes),
        "image/jpeg",
        1280,
        720,
        source
    )))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("media asset ID is already registered with different content");
    assertThat(storage.objectCount()).isEqualTo(2);
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(await(catalog.findSourceOccurrences(accepted.mediaAsset().id()))).containsExactly(
        accepted.sourceOccurrence()
    );
  }

  private static <T> T await(CompletionStage<T> stage) {
    try {
      return stage.toCompletableFuture().join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw exception;
    }
  }

  private static SourceOccurrence source(
      UUID proposedMediaAssetId,
      SourcePlatform platform,
      String sourceRecordId
  ) {
    return new SourceOccurrence(
        UUID.randomUUID(),
        proposedMediaAssetId,
        platform,
        sourceRecordId,
        null,
        null,
        null,
        null,
        "https://example.test/image.jpg",
        null,
        NOW
    );
  }
}
