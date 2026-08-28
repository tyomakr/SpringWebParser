package ru.tyomakr.akcp.core.content;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record MediaAnalysisInput(
    UUID assetId,
    String inputSha256,
    StorageReference storageReference,
    AnalysisProfileVersion profile
) {
  public MediaAnalysisInput {
    Objects.requireNonNull(assetId, "assetId is required");
    Objects.requireNonNull(inputSha256, "inputSha256 is required");
    Objects.requireNonNull(storageReference, "storageReference is required");
    Objects.requireNonNull(profile, "profile is required");
    if (!inputSha256.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException("inputSha256 must contain exactly 64 hexadecimal characters");
    }
    inputSha256 = inputSha256.toLowerCase(Locale.ROOT);
    if (!storageReference.sha256().equals(inputSha256)) {
      throw new IllegalArgumentException("storageReference must match inputSha256");
    }
  }
}
