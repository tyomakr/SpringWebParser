package ru.tyomakr.akcp.library.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.tyomakr.akcp.core.content.StorageReference;
import ru.tyomakr.akcp.core.content.StoredMedia;

class FileSystemStorageAdapterTest {
  private static final byte[] IMAGE_BYTES = "fixture-image".getBytes(StandardCharsets.UTF_8);

  @TempDir
  Path temporaryRoot;

  @TempDir
  Path outsideRoot;

  @Test
  void storesByContentHashAndReusesExistingObject() throws Exception {
    FileSystemStorageAdapter storage = new FileSystemStorageAdapter(temporaryRoot);

    StoredMedia first = storage.store(new ByteArrayInputStream(IMAGE_BYTES));
    StoredMedia second = storage.store(new ByteArrayInputStream(IMAGE_BYTES));

    assertThat(first).isEqualTo(second);
    assertThat(first.reference().value()).matches(
        "sha256/[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{64}"
    );
    assertThat(storage.exists(first.reference())).isTrue();
    try (var input = storage.open(first.reference())) {
      assertThat(input.readAllBytes()).isEqualTo(IMAGE_BYTES);
    }
    try (var paths = Files.walk(temporaryRoot.resolve("sha256"))) {
      assertThat(paths.filter(Files::isRegularFile)).hasSize(1);
    }
  }

  @Test
  void canonicalReferenceRejectsPathTraversal() {
    assertThatThrownBy(() -> new StorageReference("../../outside"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(temporaryRoot.resolveSibling("outside"))).isFalse();
  }

  @Test
  void canonicalReferenceRejectsMismatchedShards() {
    assertThatThrownBy(() -> new StorageReference("sha256/aa/bb/" + "0".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("storage reference shards must match sha256");
  }

  @Test
  void refusesSameSizeCorruptObjectAtExpectedContentAddress() throws Exception {
    FileSystemStorageAdapter storage = new FileSystemStorageAdapter(temporaryRoot);
    StoredMedia expected = new InMemoryStorageAdapter().store(
        new ByteArrayInputStream(IMAGE_BYTES)
    );
    Path target = temporaryRoot.resolve(expected.reference().value());
    Files.createDirectories(target.getParent());
    byte[] corruptBytes = IMAGE_BYTES.clone();
    corruptBytes[0] ^= 1;
    Files.write(target, corruptBytes);

    assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(IMAGE_BYTES)))
        .isInstanceOf(IOException.class)
        .hasMessage("existing content-addressed object is invalid");
    assertThat(Files.readAllBytes(target)).isEqualTo(corruptBytes);
  }

  @Test
  void rejectsSymlinkedIntermediateDirectoryWhenPlatformSupportsSymlinks() throws Exception {
    FileSystemStorageAdapter storage = new FileSystemStorageAdapter(temporaryRoot);
    Path shaDirectory = temporaryRoot.resolve("sha256");
    try {
      Files.createSymbolicLink(shaDirectory, outsideRoot);
    } catch (IOException | UnsupportedOperationException ex) {
      Assumptions.abort("Symbolic links are unavailable: " + ex.getMessage());
    }
    StorageReference reference = StorageReference.fromSha256("0".repeat(64));

    assertThatThrownBy(() -> storage.exists(reference))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("not a safe directory");
  }

  @Test
  void failedInputLeavesNoPartialObject() throws Exception {
    FileSystemStorageAdapter storage = new FileSystemStorageAdapter(temporaryRoot);
    InputStream failingInput = new InputStream() {
      private int reads;

      @Override
      public int read() throws IOException {
        if (reads++ == 0) {
          return 1;
        }
        throw new IOException("fixture failure");
      }
    };

    assertThatThrownBy(() -> storage.store(failingInput))
        .isInstanceOf(IOException.class)
        .hasMessage("fixture failure");
    try (var paths = Files.walk(temporaryRoot)) {
      assertThat(paths.filter(Files::isRegularFile)).isEmpty();
    }
  }
}
