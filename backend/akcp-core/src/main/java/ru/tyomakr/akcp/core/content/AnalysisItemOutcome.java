package ru.tyomakr.akcp.core.content;

import java.util.Objects;
import java.util.UUID;

public record AnalysisItemOutcome(UUID assetId, AnalysisItemOutcomeStatus status, String detail) {
  public AnalysisItemOutcome {
    Objects.requireNonNull(assetId, "assetId is required");
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(detail, "detail is required");
    if (detail.isBlank()) {
      throw new IllegalArgumentException("detail must not be blank");
    }
  }
}
