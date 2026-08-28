package ru.tyomakr.akcp.library.dto;

import java.util.UUID;

public record AttachmentResponse(
    UUID id,
    String type,
    String url,
    String metadata
) {
}
