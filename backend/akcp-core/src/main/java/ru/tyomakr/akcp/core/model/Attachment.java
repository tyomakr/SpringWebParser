package ru.tyomakr.akcp.core.model;

import java.util.UUID;

public record Attachment(UUID id, UUID itemId, AttachmentType type, String url, String metadata) {
}
