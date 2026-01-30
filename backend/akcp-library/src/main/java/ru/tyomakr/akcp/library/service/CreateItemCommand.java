package ru.tyomakr.akcp.library.service;

import java.util.List;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.SourceType;

public record CreateItemCommand(
    String title,
    String content,
    SourceType sourceType,
    String sourceUrl,
    List<CreateAttachment> attachments,
    List<String> tags
) {
  public record CreateAttachment(AttachmentType type, String url, String metadata) {
  }
}
