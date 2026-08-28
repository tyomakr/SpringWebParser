package ru.tyomakr.akcp.library.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateItemRequest(
    @NotBlank String title,
    String content,
    String sourceUrl,
    String sourceType,
    List<@Valid AttachmentRequest> attachments,
    List<String> tags
) {
}
