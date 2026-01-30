package ru.tyomakr.akcp.library.dto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ru.tyomakr.akcp.core.model.Attachment;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.Tag;

public final class ItemDtoMapper {
  private ItemDtoMapper() {
  }

  public static ItemResponse toResponse(Item item) {
    return new ItemResponse(
        item.id(),
        item.title(),
        item.content(),
        item.source().type().name(),
        item.source().url(),
        item.attachments().stream().map(ItemDtoMapper::toAttachment).toList(),
        item.tags().stream().map(ItemDtoMapper::toTag).collect(Collectors.toSet()),
        item.createdAt(),
        item.updatedAt()
    );
  }

  private static AttachmentResponse toAttachment(Attachment attachment) {
    return new AttachmentResponse(
        attachment.id(),
        attachment.type().name(),
        attachment.url(),
        attachment.metadata()
    );
  }

  private static TagResponse toTag(Tag tag) {
    return new TagResponse(tag.id(), tag.name());
  }

  public static ItemListResponse toListResponse(List<Item> items, String nextCursor) {
    List<ItemResponse> responses = items.stream().map(ItemDtoMapper::toResponse).toList();
    return new ItemListResponse(responses, nextCursor);
  }
}
