package ru.tyomakr.akcp.library.persistence;

import java.util.UUID;

public record AttachmentRow(
    UUID id,
    UUID itemId,
    String type,
    String url,
    String metadata
) {
}
