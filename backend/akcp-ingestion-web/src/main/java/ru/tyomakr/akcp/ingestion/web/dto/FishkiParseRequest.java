package ru.tyomakr.akcp.ingestion.web.dto;

public record FishkiParseRequest(Integer pageFrom, Integer pageTo, Boolean createItem) {
  public int resolvedPageFrom() {
    return pageFrom == null ? 1 : pageFrom;
  }

  public int resolvedPageTo() {
    return pageTo == null ? resolvedPageFrom() : pageTo;
  }

  public boolean resolvedCreateItem() {
    return createItem == null || createItem;
  }
}
