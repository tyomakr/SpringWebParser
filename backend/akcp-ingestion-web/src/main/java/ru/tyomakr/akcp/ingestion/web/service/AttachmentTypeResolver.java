package ru.tyomakr.akcp.ingestion.web.service;

import java.util.Locale;
import ru.tyomakr.akcp.core.model.AttachmentType;

public final class AttachmentTypeResolver {
  private AttachmentTypeResolver() {
  }

  public static AttachmentType resolve(String url) {
    if (url == null) {
      return AttachmentType.FILE;
    }
    String normalized = url;
    int queryIndex = normalized.indexOf('?');
    if (queryIndex >= 0) {
      normalized = normalized.substring(0, queryIndex);
    }
    int hashIndex = normalized.indexOf('#');
    if (hashIndex >= 0) {
      normalized = normalized.substring(0, hashIndex);
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".mp4") || lower.endsWith(".webm")) {
      return AttachmentType.VIDEO;
    }
    if (lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".gif")
        || lower.endsWith(".webp")
        || lower.endsWith(".bmp")) {
      return AttachmentType.IMAGE;
    }
    return AttachmentType.FILE;
  }
}
