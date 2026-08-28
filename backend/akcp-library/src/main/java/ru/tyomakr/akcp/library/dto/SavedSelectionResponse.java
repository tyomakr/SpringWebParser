package ru.tyomakr.akcp.library.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.tyomakr.akcp.core.model.SavedSelection;

public record SavedSelectionResponse(
    UUID id,
    UUID itemId,
    List<UUID> attachmentIds,
    String target,
    Instant createdAt,
    Instant expiresAt
) {
  public static SavedSelectionResponse from(SavedSelection selection) {
    return new SavedSelectionResponse(
        selection.id(),
        selection.itemId(),
        selection.attachmentIds(),
        selection.target(),
        selection.createdAt(),
        selection.expiresAt()
    );
  }
}
