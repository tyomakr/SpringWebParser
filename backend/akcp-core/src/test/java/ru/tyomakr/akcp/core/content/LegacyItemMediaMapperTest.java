package ru.tyomakr.akcp.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.model.Attachment;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.SourceRef;
import ru.tyomakr.akcp.core.model.SourceType;

class LegacyItemMediaMapperTest {
  private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

  @Test
  void mapsLegacyReferencesWithoutInventingContentIdentity() {
    UUID mediaAssetId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID attachmentId = UUID.randomUUID();
    Item item = item(itemId, SourceType.VK, "https://vk.example/post/10");
    Attachment attachment = new Attachment(
        attachmentId,
        itemId,
        AttachmentType.IMAGE,
        "https://cdn.example/image.jpg",
        "{\"legacy\":true}"
    );

    SourceOccurrence result = LegacyItemMediaMapper.toSourceOccurrence(
        mediaAssetId,
        item,
        attachment,
        NOW
    );

    assertEquals(attachmentId, result.id());
    assertEquals(mediaAssetId, result.mediaAssetId());
    assertEquals(SourcePlatform.VK, result.platform());
    assertEquals(
        "legacy:item:" + itemId + "/attachment:" + attachmentId,
        result.sourceRecordId()
    );
    assertNull(result.sourceConnectionId());
    assertNull(result.externalPostId());
    assertNull(result.externalMediaId());
    assertEquals(item.source().url(), result.postUrl());
    assertEquals(attachment.url(), result.mediaUrl());
    assertEquals(attachment.metadata(), result.metadata());
    assertEquals(NOW, result.discoveredAt());
  }

  @Test
  void mapsEveryExistingLegacySourceType() {
    for (SourceType sourceType : SourceType.values()) {
      UUID itemId = UUID.randomUUID();
      Item item = item(itemId, sourceType, "https://example.test/post");
      Attachment attachment = new Attachment(
          UUID.randomUUID(),
          itemId,
          AttachmentType.IMAGE,
          "https://example.test/image.jpg",
          null
      );

      SourceOccurrence result = LegacyItemMediaMapper.toSourceOccurrence(
          UUID.randomUUID(),
          item,
          attachment,
          NOW
      );

      assertEquals(SourcePlatform.valueOf(sourceType.name()), result.platform());
    }
  }

  @Test
  void rejectsAttachmentFromAnotherItem() {
    Item item = item(UUID.randomUUID(), SourceType.WEB, "https://example.test/post");
    Attachment attachment = new Attachment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        AttachmentType.IMAGE,
        "https://example.test/image.jpg",
        null
    );

    assertThrows(
        IllegalArgumentException.class,
        () -> LegacyItemMediaMapper.toSourceOccurrence(
            UUID.randomUUID(),
            item,
            attachment,
            NOW
        )
    );
  }

  @Test
  void rejectsNonImageAttachment() {
    UUID itemId = UUID.randomUUID();
    Item item = item(itemId, SourceType.WEB, "https://example.test/post");
    Attachment attachment = new Attachment(
        UUID.randomUUID(),
        itemId,
        AttachmentType.VIDEO,
        "https://example.test/video.mp4",
        null
    );

    assertThrows(
        IllegalArgumentException.class,
        () -> LegacyItemMediaMapper.toSourceOccurrence(
            UUID.randomUUID(),
            item,
            attachment,
            NOW
        )
    );
  }

  @Test
  void rejectsMissingLegacyItemIdWithControlledError() {
    Item item = item(null, SourceType.WEB, "https://example.test/post");
    Attachment attachment = new Attachment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        AttachmentType.IMAGE,
        "https://example.test/image.jpg",
        null
    );

    IllegalArgumentException error = assertThrows(
        IllegalArgumentException.class,
        () -> LegacyItemMediaMapper.toSourceOccurrence(
            UUID.randomUUID(),
            item,
            attachment,
            NOW
        )
    );

    assertEquals("item.id is required", error.getMessage());
  }

  private static Item item(UUID itemId, SourceType sourceType, String sourceUrl) {
    return new Item(
        itemId,
        "Legacy title",
        "Legacy content",
        new SourceRef(sourceType, sourceUrl),
        List.of(),
        Set.of(),
        NOW,
        NOW
    );
  }
}
