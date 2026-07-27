package ru.tyomakr.akcp.core.content;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record MediaAsset(
    UUID id,
    String sha256,
    String mimeType,
    int width,
    int height,
    StorageReference storageReference
) {
  public MediaAsset {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(sha256, "sha256 is required");
    Objects.requireNonNull(mimeType, "mimeType is required");
    Objects.requireNonNull(storageReference, "storageReference is required");
    if (!sha256.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
    }
    if (mimeType.isBlank()) {
      throw new IllegalArgumentException("mimeType must not be blank");
    }
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("width and height must be positive");
    }
    sha256 = sha256.toLowerCase(Locale.ROOT);
    if (!storageReference.sha256().equals(sha256)) {
      throw new IllegalArgumentException("storageReference must match sha256");
    }
  }
}
