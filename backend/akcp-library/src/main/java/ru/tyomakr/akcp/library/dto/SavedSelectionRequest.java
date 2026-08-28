package ru.tyomakr.akcp.library.dto;

import java.util.List;
import java.util.UUID;

public record SavedSelectionRequest(UUID itemId, List<UUID> attachmentIds, String target) {
}
