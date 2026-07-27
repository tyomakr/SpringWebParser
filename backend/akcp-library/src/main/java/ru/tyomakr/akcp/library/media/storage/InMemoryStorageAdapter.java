package ru.tyomakr.akcp.library.media.storage;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import ru.tyomakr.akcp.core.content.StoragePort;
import ru.tyomakr.akcp.core.content.StorageReference;
import ru.tyomakr.akcp.core.content.StoredMedia;

/**
 * Bounded-test and development adapter. Production callers should use a streaming adapter.
 */
public final class InMemoryStorageAdapter implements StoragePort {
  private final Map<StorageReference, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public StoredMedia store(InputStream content) throws IOException {
    Objects.requireNonNull(content, "content is required");
    byte[] bytes = content.readAllBytes();
    StorageReference reference = StorageReference.fromSha256(sha256(bytes));
    objects.putIfAbsent(reference, bytes.clone());
    return new StoredMedia(reference, bytes.length);
  }

  @Override
  public InputStream open(StorageReference reference) throws IOException {
    Objects.requireNonNull(reference, "reference is required");
    byte[] bytes = objects.get(reference);
    if (bytes == null) {
      throw new FileNotFoundException(reference.value());
    }
    return new ByteArrayInputStream(bytes.clone());
  }

  @Override
  public boolean exists(StorageReference reference) {
    Objects.requireNonNull(reference, "reference is required");
    return objects.containsKey(reference);
  }

  public int objectCount() {
    return objects.size();
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
