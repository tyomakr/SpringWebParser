package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record AnalysisTextEvidence(
    Double areaRatio,
    AnalysisTextRole role,
    double confidence,
    String providerVersion,
    String source
) {
  public AnalysisTextEvidence {
    Objects.requireNonNull(role, "role is required");
    Objects.requireNonNull(providerVersion, "providerVersion is required");
    Objects.requireNonNull(source, "source is required");
    if (providerVersion.isBlank() || source.isBlank()) {
      throw new IllegalArgumentException("providerVersion and source must not be blank");
    }
    if (!Double.isFinite(confidence) || confidence < 0.0d || confidence > 1.0d) {
      throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
    if (areaRatio == null) {
      if (role != AnalysisTextRole.UNKNOWN) {
        throw new IllegalArgumentException("unknown text role is required when areaRatio is absent");
      }
    } else {
      if (role == AnalysisTextRole.UNKNOWN) {
        throw new IllegalArgumentException("known text role is required when areaRatio is present");
      }
      if (!Double.isFinite(areaRatio) || areaRatio < 0.0d || areaRatio > 1.0d) {
        throw new IllegalArgumentException("areaRatio must be between 0 and 1");
      }
    }
  }

  public static AnalysisTextEvidence unknown(String providerVersion) {
    return new AnalysisTextEvidence(null, AnalysisTextRole.UNKNOWN, 0.0d, providerVersion, "unknown");
  }

  public boolean isDominant(double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0.0d || threshold > 1.0d) {
      throw new IllegalArgumentException("threshold must be between 0 and 1");
    }
    return areaRatio != null
        && role == AnalysisTextRole.PRIMARY
        && areaRatio >= threshold;
  }
}
