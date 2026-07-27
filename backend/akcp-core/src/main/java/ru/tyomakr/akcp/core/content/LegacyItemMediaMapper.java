package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import ru.tyomakr.akcp.core.model.Attachment;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.SourceType;

public final class LegacyItemMediaMapper {
  private LegacyItemMediaMapper() {
  }

  public static SourceOccurrence toSourceOccurrence(
      UUID mediaAssetId,
      Item item,
      Attachment attachment,
      Instant discoveredAt
  ) {
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(item, "item is required");
    Objects.requireNonNull(attachment, "attachment is required");
    Objects.requireNonNull(discoveredAt, "discoveredAt is required");
    requireLegacyId(item.id(), "item.id");
    requireLegacyId(attachment.id(), "attachment.id");
    requireLegacyId(attachment.itemId(), "attachment.itemId");
    if (!item.id().equals(attachment.itemId())) {
      throw new IllegalArgumentException("attachment does not belong to item");
    }
    if (attachment.type() != AttachmentType.IMAGE) {
      throw new IllegalArgumentException("only image attachments can be mapped to media");
    }
    if (item.source() == null) {
      throw new IllegalArgumentException("item source is required");
    }

    return new SourceOccurrence(
        attachment.id(),
        mediaAssetId,
        toSourcePlatform(item.source().type()),
        "legacy:item:" + item.id() + "/attachment:" + attachment.id(),
        null,
        null,
        null,
        item.source().url(),
        attachment.url(),
        attachment.metadata(),
        discoveredAt
    );
  }

  private static SourcePlatform toSourcePlatform(SourceType sourceType) {
    if (sourceType == null) {
      throw new IllegalArgumentException("source type is required");
    }
    return switch (sourceType) {
      case MANUAL -> SourcePlatform.MANUAL;
      case WEB -> SourcePlatform.WEB;
      case TELEGRAM -> SourcePlatform.TELEGRAM;
      case VK -> SourcePlatform.VK;
    };
  }

  private static void requireLegacyId(UUID id, String fieldName) {
    if (id == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
  }
}
