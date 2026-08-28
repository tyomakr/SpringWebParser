package ru.tyomakr.akcp.library.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachmentRequest(
    @NotBlank String type,
    @NotBlank String url,
    String metadata
) {
}
