package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record AnalysisProviderDescriptor(
    String providerId,
    String version,
    String signalType,
    AnalysisArtifactManifest artifactManifest,
    boolean networkAccessRequired
) {
  public AnalysisProviderDescriptor {
    Objects.requireNonNull(providerId, "providerId is required");
    Objects.requireNonNull(version, "version is required");
    Objects.requireNonNull(signalType, "signalType is required");
    Objects.requireNonNull(artifactManifest, "artifactManifest is required");
    if (providerId.isBlank() || version.isBlank() || signalType.isBlank()) {
      throw new IllegalArgumentException("provider descriptor values must not be blank");
    }
    if (networkAccessRequired) {
      throw new IllegalArgumentException("analysis providers must not require network access");
    }
  }
}
