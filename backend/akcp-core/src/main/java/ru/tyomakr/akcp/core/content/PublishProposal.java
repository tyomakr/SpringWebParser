package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Explicit proposal required by a publisher, carrying a separate approval record. */
public record PublishProposal(
    UUID proposalId,
    PublicationPlatform destination,
    String destinationConnectionId,
    List<UUID> mediaAssetIds,
    PublicationApproval approval,
    String idempotencyKey,
    Instant requestedAt
) {
  public PublishProposal {
    Objects.requireNonNull(proposalId, "proposalId is required");
    Objects.requireNonNull(destination, "destination is required");
    Objects.requireNonNull(mediaAssetIds, "mediaAssetIds are required");
    if (mediaAssetIds.isEmpty()) {
      throw new IllegalArgumentException("mediaAssetIds must not be empty");
    }
    if (mediaAssetIds.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("mediaAssetIds must not contain nulls");
    }
    mediaAssetIds = List.copyOf(mediaAssetIds);
    destinationConnectionId = requireNonBlank(destinationConnectionId, "destinationConnectionId");
    Objects.requireNonNull(approval, "approval is required");
    if (approval.destination() != destination) {
      throw new IllegalArgumentException("approval destination does not match proposal destination");
    }
    idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
    requestedAt = Objects.requireNonNull(requestedAt, "requestedAt is required");
  }

  private static String requireNonBlank(String value, String name) {
    Objects.requireNonNull(value, name + " is required");
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }
}
