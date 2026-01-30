package ru.tyomakr.akcp.library.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import io.r2dbc.spi.Readable;
import ru.tyomakr.akcp.library.persistence.AttachmentRow;
import ru.tyomakr.akcp.library.persistence.ItemRow;
import ru.tyomakr.akcp.library.persistence.ItemTagRow;
import ru.tyomakr.akcp.library.persistence.TagRow;

final class RowMappers {
  private RowMappers() {
  }

  static ItemRow toItemRow(Readable row) {
    OffsetDateTime createdAt = row.get("created_at", OffsetDateTime.class);
    OffsetDateTime updatedAt = row.get("updated_at", OffsetDateTime.class);
    return new ItemRow(
        row.get("id", UUID.class),
        row.get("title", String.class),
        row.get("content", String.class),
        row.get("source_type", String.class),
        row.get("source_url", String.class),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null
    );
  }

  static AttachmentRow toAttachmentRow(Readable row) {
    return new AttachmentRow(
        row.get("id", UUID.class),
        row.get("item_id", UUID.class),
        row.get("type", String.class),
        row.get("url", String.class),
        row.get("metadata", String.class)
    );
  }

  static TagRow toTagRow(Readable row) {
    return new TagRow(
        row.get("id", UUID.class),
        row.get("name", String.class)
    );
  }

  static ItemTagRow toItemTagRow(Readable row) {
    TagRow tag = new TagRow(
        row.get("tag_id", UUID.class),
        row.get("name", String.class)
    );
    return new ItemTagRow(row.get("item_id", UUID.class), tag);
  }
}
