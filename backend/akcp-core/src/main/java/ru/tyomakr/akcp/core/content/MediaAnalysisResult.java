package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MediaAnalysisResult(
    UUID assetId,
    String inputSha256,
    AnalysisProfileVersion profile,
    AnalysisProviderDescriptor provider,
    int width,
    int height,
    long perceptualHash,
    List<Double> visualVector,
    AnalysisTextEvidence textEvidence,
    List<AnalysisExplanation> explanations
) {
  public MediaAnalysisResult {
    Objects.requireNonNull(assetId, "assetId is required");
    Objects.requireNonNull(inputSha256, "inputSha256 is required");
    Objects.requireNonNull(profile, "profile is required");
    Objects.requireNonNull(provider, "provider is required");
    Objects.requireNonNull(visualVector, "visualVector is required");
    Objects.requireNonNull(textEvidence, "textEvidence is required");
    Objects.requireNonNull(explanations, "explanations is required");
    if (!inputSha256.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException("inputSha256 must contain exactly 64 hexadecimal characters");
    }
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("width and height must be positive");
    }
    if (visualVector.isEmpty() || visualVector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
      throw new IllegalArgumentException("visualVector must contain finite values");
    }
    inputSha256 = inputSha256.toLowerCase(java.util.Locale.ROOT);
    visualVector = List.copyOf(visualVector);
    explanations = List.copyOf(explanations);
  }
}
