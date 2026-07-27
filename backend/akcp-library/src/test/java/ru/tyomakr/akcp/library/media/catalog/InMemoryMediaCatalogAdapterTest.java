package ru.tyomakr.akcp.library.media.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.core.content.StorageReference;

class InMemoryMediaCatalogAdapterTest {
  private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

  @Test
  void canonicalizesEqualContentFromDifferentSources() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset firstCandidate = asset(UUID.randomUUID(), "a".repeat(64));
    MediaAsset duplicateCandidate = asset(UUID.randomUUID(), "a".repeat(64));

    CatalogRegistration first = catalog.register(
        firstCandidate,
        source(firstCandidate.id(), SourcePlatform.VK, "vk:photo:1")
    );
    CatalogRegistration duplicate = catalog.register(
        duplicateCandidate,
        source(duplicateCandidate.id(), SourcePlatform.WEB, "web:image:1")
    );

    assertThat(first.mediaAssetCreated()).isTrue();
    assertThat(duplicate.mediaAssetCreated()).isFalse();
    assertThat(duplicate.sourceOccurrenceCreated()).isTrue();
    assertThat(duplicate.mediaAsset()).isEqualTo(first.mediaAsset());
    assertThat(duplicate.sourceOccurrence().mediaAssetId()).isEqualTo(first.mediaAsset().id());
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(catalog.sourceOccurrenceCount()).isEqualTo(2);
  }

  @Test
  void repeatedSourceRecordIsIdempotent() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset candidate = asset(UUID.randomUUID(), "b".repeat(64));
    SourceOccurrence occurrence = source(candidate.id(), SourcePlatform.VK, "vk:photo:2");

    CatalogRegistration first = catalog.register(candidate, occurrence);
    CatalogRegistration repeated = catalog.register(candidate, occurrence);

    assertThat(first.sourceOccurrenceCreated()).isTrue();
    assertThat(repeated.mediaAssetCreated()).isFalse();
    assertThat(repeated.sourceOccurrenceCreated()).isFalse();
    assertThat(repeated.sourceOccurrence()).isEqualTo(first.sourceOccurrence());
  }

  @Test
  void sourceRecordCannotSilentlyChangeContent() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset original = asset(UUID.randomUUID(), "c".repeat(64));
    MediaAsset changed = asset(UUID.randomUUID(), "d".repeat(64));
    catalog.register(original, source(original.id(), SourcePlatform.VK, "vk:photo:3"));

    assertThatThrownBy(() -> catalog.register(
        changed,
        source(changed.id(), SourcePlatform.VK, "vk:photo:3")
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("source record already points to different content");
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(catalog.sourceOccurrenceCount()).isEqualTo(1);
  }

  @Test
  void candidateIdCannotBeReusedForDifferentContentDuringShaDeduplication() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset first = asset(UUID.randomUUID(), "e".repeat(64));
    MediaAsset second = asset(UUID.randomUUID(), "f".repeat(64));
    catalog.register(first, source(first.id(), SourcePlatform.VK, "vk:photo:4"));
    catalog.register(second, source(second.id(), SourcePlatform.WEB, "web:image:4"));
    MediaAsset collidingCandidate = asset(second.id(), first.sha256());

    assertThatThrownBy(() -> catalog.register(
        collidingCandidate,
        source(collidingCandidate.id(), SourcePlatform.MANUAL, "manual:image:4")
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("media asset ID is already registered with different content");
    assertThat(catalog.assetCount()).isEqualTo(2);
    assertThat(catalog.sourceOccurrenceCount()).isEqualTo(2);
  }

  @Test
  void equalShaWithConflictingDimensionsIsRejected() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset canonical = asset(UUID.randomUUID(), "1".repeat(64));
    catalog.register(
        canonical,
        source(canonical.id(), SourcePlatform.VK, "vk:photo:5")
    );
    MediaAsset conflicting = new MediaAsset(
        UUID.randomUUID(),
        canonical.sha256(),
        canonical.mimeType(),
        canonical.width() + 1,
        canonical.height(),
        canonical.storageReference()
    );

    assertThatThrownBy(() -> catalog.register(
        conflicting,
        source(conflicting.id(), SourcePlatform.WEB, "web:image:5")
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("content metadata conflicts with canonical media asset");
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(catalog.sourceOccurrenceCount()).isEqualTo(1);
  }

  @Test
  void repeatedSourceWithConflictingDimensionsIsRejected() {
    InMemoryMediaCatalogAdapter catalog = new InMemoryMediaCatalogAdapter();
    MediaAsset canonical = asset(UUID.randomUUID(), "2".repeat(64));
    SourceOccurrence occurrence = source(
        canonical.id(),
        SourcePlatform.VK,
        "vk:photo:6"
    );
    catalog.register(canonical, occurrence);
    MediaAsset conflicting = new MediaAsset(
        UUID.randomUUID(),
        canonical.sha256(),
        canonical.mimeType(),
        canonical.width() + 1,
        canonical.height(),
        canonical.storageReference()
    );

    assertThatThrownBy(() -> catalog.register(
        conflicting,
        source(conflicting.id(), SourcePlatform.VK, "vk:photo:6")
    ))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("content metadata conflicts with canonical media asset");
    assertThat(catalog.assetCount()).isEqualTo(1);
    assertThat(catalog.sourceOccurrenceCount()).isEqualTo(1);
  }

  private static MediaAsset asset(UUID id, String sha256) {
    return new MediaAsset(
        id,
        sha256,
        "image/jpeg",
        1280,
        720,
        StorageReference.fromSha256(sha256)
    );
  }

  private static SourceOccurrence source(
      UUID mediaAssetId,
      SourcePlatform platform,
      String sourceRecordId
  ) {
    return new SourceOccurrence(
        UUID.randomUUID(),
        mediaAssetId,
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
