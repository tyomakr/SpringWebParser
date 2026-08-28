package ru.tyomakr.akcp.ingestion.web.service;

import java.util.UUID;

public record FishkiParseJobPayload(
    int pageFrom,
    int pageTo,
    boolean createItem,
    UUID createdItemId,
    Integer attachmentsCount
) {
  public FishkiParseJobPayload withResult(UUID createdItemId, int attachmentsCount) {
    return new FishkiParseJobPayload(pageFrom, pageTo, createItem, createdItemId, attachmentsCount);
  }
}
