package ru.tyomakr.akcp.ingestion.web.dto;

import java.util.List;
import java.util.UUID;

public record FishkiParseResult(
    String baseUrl,
    int pageFrom,
    int pageTo,
    int pagesParsed,
    List<ParsedAttachment> attachments,
    UUID createdItemId
) {
  public FishkiParseResult withCreatedItemId(UUID createdItemId) {
    return new FishkiParseResult(baseUrl, pageFrom, pageTo, pagesParsed, attachments, createdItemId);
  }
}
