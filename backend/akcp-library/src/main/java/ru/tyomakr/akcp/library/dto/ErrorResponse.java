package ru.tyomakr.akcp.library.dto;

import java.time.Instant;

public record ErrorResponse(String message, Instant timestamp) {
}
