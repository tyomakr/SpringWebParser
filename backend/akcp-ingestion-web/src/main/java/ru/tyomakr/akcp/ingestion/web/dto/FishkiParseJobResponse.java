package ru.tyomakr.akcp.ingestion.web.dto;

import java.util.UUID;

public record FishkiParseJobResponse(UUID jobId, String status) {
}
