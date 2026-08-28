package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record StoredMedia(StorageReference reference, long byteSize) {
  public StoredMedia {
    Objects.requireNonNull(reference, "reference is required");
    if (byteSize < 0) {
      throw new IllegalArgumentException("byteSize must not be negative");
    }
  }

  public String sha256() {
    return reference.sha256();
  }
}
