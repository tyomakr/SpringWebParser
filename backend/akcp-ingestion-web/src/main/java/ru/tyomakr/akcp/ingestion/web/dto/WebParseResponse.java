package ru.tyomakr.akcp.ingestion.web.dto;

import java.util.List;
import java.util.UUID;

public record WebParseResponse(
    String url,
    String title,
    List<ParsedAttachment> attachments,
    UUID createdItemId
) {
}
