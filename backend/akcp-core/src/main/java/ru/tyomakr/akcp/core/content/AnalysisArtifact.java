package ru.tyomakr.akcp.core.content;

import java.util.Locale;
import java.util.Objects;

public record AnalysisArtifact(
    String artifactId,
    String version,
    String license,
    String sha256,
    long sizeBytes,
    String location
) {
  public AnalysisArtifact {
    Objects.requireNonNull(artifactId, "artifactId is required");
    Objects.requireNonNull(version, "version is required");
    Objects.requireNonNull(license, "license is required");
    Objects.requireNonNull(sha256, "sha256 is required");
    Objects.requireNonNull(location, "location is required");
    if (artifactId.isBlank() || version.isBlank() || license.isBlank() || location.isBlank()) {
      throw new IllegalArgumentException("artifact metadata must not be blank");
    }
    if (!sha256.toLowerCase(Locale.ROOT).matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("sha256 must contain exactly 64 lowercase hexadecimal characters");
    }
    if (sizeBytes <= 0L) {
      throw new IllegalArgumentException("sizeBytes must be positive");
    }
    String lowerLocation = location.toLowerCase(Locale.ROOT);
    if (!lowerLocation.startsWith("file:")
        && !lowerLocation.startsWith("classpath:")
        && !lowerLocation.startsWith("embedded:")) {
      throw new IllegalArgumentException("artifact location must be file, classpath or embedded");
    }
    sha256 = sha256.toLowerCase(Locale.ROOT);
  }
}
