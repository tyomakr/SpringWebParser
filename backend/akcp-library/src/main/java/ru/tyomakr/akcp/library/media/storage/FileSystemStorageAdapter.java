package ru.tyomakr.akcp.library.media.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import ru.tyomakr.akcp.core.content.StoragePort;
import ru.tyomakr.akcp.core.content.StorageReference;
import ru.tyomakr.akcp.core.content.StoredMedia;

/**
 * Content-addressed storage for an exclusive, operator-controlled root on a filesystem that
 * supports atomic moves within one volume.
 */
public final class FileSystemStorageAdapter implements StoragePort {
  private static final int BUFFER_SIZE = 8192;

  private final Path root;
  private final Path temporaryDirectory;

  public FileSystemStorageAdapter(Path root) throws IOException {
    Objects.requireNonNull(root, "root is required");
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Files.createDirectories(normalizedRoot);
    if (Files.isSymbolicLink(normalizedRoot)) {
      throw new IOException("storage root must not be a symbolic link");
    }
    this.root = normalizedRoot.toRealPath();
    this.temporaryDirectory = ensureChildDirectory(this.root, ".tmp");
  }

  @Override
  public StoredMedia store(InputStream content) throws IOException {
    Objects.requireNonNull(content, "content is required");
    Path temporaryFile = Files.createTempFile(temporaryDirectory, "store-", ".part");
    MessageDigest digest = sha256Digest();
    long byteSize = 0;

    try {
      try (OutputStream output = Files.newOutputStream(
          temporaryFile,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
      )) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = content.read(buffer)) != -1) {
          output.write(buffer, 0, read);
          digest.update(buffer, 0, read);
          byteSize += read;
        }
      }

      StorageReference reference = StorageReference.fromSha256(
          HexFormat.of().formatHex(digest.digest())
      );
      Path target = resolveForWrite(reference);
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        verifyExistingTarget(target, reference, byteSize);
        return new StoredMedia(reference, byteSize);
      }

      moveIntoPlace(temporaryFile, target, reference, byteSize);
      return new StoredMedia(reference, byteSize);
    } finally {
      Files.deleteIfExists(temporaryFile);
    }
  }

  @Override
  public InputStream open(StorageReference reference) throws IOException {
    Path target = resolveForRead(reference);
    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileNotFoundException(reference.value());
    }
    return Files.newInputStream(target, StandardOpenOption.READ);
  }

  @Override
  public boolean exists(StorageReference reference) throws IOException {
    Path target = resolveForRead(reference);
    return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
  }

  private Path resolveForWrite(StorageReference reference) throws IOException {
    Objects.requireNonNull(reference, "reference is required");
    String[] segments = reference.value().split("/");
    Path parent = root;
    for (int index = 0; index < segments.length - 1; index++) {
      parent = ensureChildDirectory(parent, segments[index]);
    }
    return safeResolve(parent, segments[segments.length - 1]);
  }

  private Path resolveForRead(StorageReference reference) throws IOException {
    Objects.requireNonNull(reference, "reference is required");
    String[] segments = reference.value().split("/");
    Path parent = root;
    for (int index = 0; index < segments.length - 1; index++) {
      parent = safeResolve(parent, segments[index]);
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
          && (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(parent))) {
        throw new IOException("storage path component is not a safe directory: " + segments[index]);
      }
    }
    return safeResolve(parent, segments[segments.length - 1]);
  }

  private Path ensureChildDirectory(Path parent, String child) throws IOException {
    Path directory = safeResolve(parent, child);
    try {
      Files.createDirectory(directory);
    } catch (FileAlreadyExistsException ex) {
      if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(directory)) {
        throw new IOException("storage path component is not a safe directory: " + child, ex);
      }
    }
    if (Files.isSymbolicLink(directory)) {
      throw new IOException("storage path component must not be a symbolic link: " + child);
    }
    return directory;
  }

  private Path safeResolve(Path parent, String child) {
    Path resolved = parent.resolve(child).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("storage path escapes configured root");
    }
    return resolved;
  }

  private void verifyExistingTarget(
      Path target,
      StorageReference reference,
      long expectedSize
  ) throws IOException {
    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        || Files.size(target) != expectedSize
        || !sha256(target).equals(reference.sha256())) {
      throw new IOException("existing content-addressed object is invalid");
    }
  }

  private void moveIntoPlace(
      Path temporaryFile,
      Path target,
      StorageReference reference,
      long byteSize
  ) throws IOException {
    try {
      Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (FileAlreadyExistsException ex) {
      verifyExistingTarget(target, reference, byteSize);
    }
  }

  private static String sha256(Path path) throws IOException {
    MessageDigest digest = sha256Digest();
    try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
