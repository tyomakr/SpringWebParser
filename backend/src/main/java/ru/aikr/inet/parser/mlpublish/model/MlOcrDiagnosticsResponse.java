package ru.aikr.inet.parser.mlpublish.model;

import com.fasterxml.jackson.databind.JsonNode;

public record MlOcrDiagnosticsResponse(
        boolean available,
        JsonNode report,
        String error
) {
}
