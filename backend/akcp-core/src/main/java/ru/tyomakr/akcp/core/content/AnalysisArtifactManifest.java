package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record AnalysisArtifactManifest(String manifestVersion, List<AnalysisArtifact> artifacts) {
  public AnalysisArtifactManifest {
    Objects.requireNonNull(manifestVersion, "manifestVersion is required");
    Objects.requireNonNull(artifacts, "artifacts is required");
    if (manifestVersion.isBlank()) {
      throw new IllegalArgumentException("manifestVersion must not be blank");
    }
    artifacts = List.copyOf(artifacts);
    Set<String> artifactIds = new HashSet<>();
    for (AnalysisArtifact artifact : artifacts) {
      if (!artifactIds.add(artifact.artifactId())) {
        throw new IllegalArgumentException("artifactId must be unique: " + artifact.artifactId());
      }
    }
  }

  public static AnalysisArtifactManifest empty() {
    return new AnalysisArtifactManifest("1", List.of());
  }
}
