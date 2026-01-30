package ru.tyomakr.akcp.ingestion.web.dto;

import jakarta.validation.constraints.NotBlank;

public record WebParseRequest(@NotBlank String url, boolean createItem) {
}
