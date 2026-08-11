package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublicationAttemptResult(
    UUID proposalId,
    PublicationPlatform destination,
    PublicationAttemptStatus status,
    List<String> externalPublicationIds,
    Instant observedAt,
    String detail
) {
  public PublicationAttemptResult {
    Objects.requireNonNull(proposalId, "proposalId is required");
    Objects.requireNonNull(destination, "destination is required");
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(externalPublicationIds, "externalPublicationIds are required");
    if (externalPublicationIds.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("externalPublicationIds must not contain blanks");
    }
    if (status == PublicationAttemptStatus.SUCCEEDED && externalPublicationIds.isEmpty()) {
      throw new IllegalArgumentException("SUCCEEDED attempt requires external publication IDs");
    }
    externalPublicationIds = externalPublicationIds.stream().map(String::trim).toList();
    observedAt = Objects.requireNonNull(observedAt, "observedAt is required");
    detail = detail == null || detail.isBlank() ? null : detail.trim();
  }
}
