package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Explicit human/operator approval attached to a publication proposal. */
public record PublicationApproval(
    UUID approvalId,
    PublicationPlatform destination,
    String operatorReference,
    Instant approvedAt
) {
  public PublicationApproval {
    Objects.requireNonNull(approvalId, "approvalId is required");
    Objects.requireNonNull(destination, "destination is required");
    Objects.requireNonNull(approvedAt, "approvedAt is required");
    operatorReference = requireNonBlank(operatorReference, "operatorReference");
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
