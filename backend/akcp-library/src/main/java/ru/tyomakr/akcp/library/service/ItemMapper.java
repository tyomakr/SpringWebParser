package ru.tyomakr.akcp.library.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ru.tyomakr.akcp.core.model.Attachment;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.SourceRef;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.core.model.Tag;
import ru.tyomakr.akcp.library.persistence.AttachmentRow;
import ru.tyomakr.akcp.library.persistence.ItemRow;
import ru.tyomakr.akcp.library.persistence.TagRow;

final class ItemMapper {
  private ItemMapper() {
  }

  static Item toItem(ItemRow row, List<AttachmentRow> attachments, Set<TagRow> tags) {
    return new Item(
        row.id(),
        row.title(),
        row.content(),
        new SourceRef(SourceType.valueOf(row.sourceType()), row.sourceUrl()),
        attachments.stream().map(ItemMapper::toAttachment).toList(),
        tags.stream().map(ItemMapper::toTag).collect(Collectors.toSet()),
        row.createdAt(),
        row.updatedAt()
    );
  }

  static Attachment toAttachment(AttachmentRow row) {
    return new Attachment(
        row.id(),
        row.itemId(),
        AttachmentType.valueOf(row.type()),
        row.url(),
        row.metadata()
    );
  }

  static Tag toTag(TagRow row) {
    return new Tag(row.id(), row.name());
  }
}
