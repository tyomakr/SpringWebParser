package ru.tyomakr.akcp.core.content;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record StorageReference(String value) {
  private static final Pattern CONTENT_ADDRESS = Pattern.compile(
      "sha256/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{64}"
  );

  public StorageReference {
    Objects.requireNonNull(value, "value is required");
    if (!CONTENT_ADDRESS.matcher(value).matches()) {
      throw new IllegalArgumentException("value must be a canonical sha256 storage reference");
    }
    String[] segments = value.split("/");
    String digest = segments[3];
    if (!segments[1].equals(digest.substring(0, 2))
        || !segments[2].equals(digest.substring(2, 4))) {
      throw new IllegalArgumentException("storage reference shards must match sha256");
    }
  }

  public static StorageReference fromSha256(String sha256) {
    Objects.requireNonNull(sha256, "sha256 is required");
    String normalized = sha256.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
    }
    return new StorageReference(
        "sha256/" + normalized.substring(0, 2) + "/" + normalized.substring(2, 4) + "/"
            + normalized
    );
  }

  public String sha256() {
    return value.substring(value.length() - 64);
  }
}
