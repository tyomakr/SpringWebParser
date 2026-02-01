package ru.tyomakr.akcp.ingestion.web.dto;

import java.util.UUID;

public record FishkiParseJobStatus(
    UUID jobId,
    String status,
    Integer pageFrom,
    Integer pageTo,
    UUID createdItemId,
    Integer attachmentsCount,
    String lastError
) {
}
