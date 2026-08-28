package ru.tyomakr.akcp.library.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.content.StorageReference;
import ru.tyomakr.akcp.core.content.StoredMedia;

class InMemoryStorageAdapterTest {
  private static final byte[] IMAGE_BYTES = "fixture-image".getBytes(StandardCharsets.UTF_8);

  @Test
  void storesIdenticalBytesOnceAndReturnsDefensiveReads() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();

    StoredMedia first = storage.store(new ByteArrayInputStream(IMAGE_BYTES));
    StoredMedia second = storage.store(new ByteArrayInputStream(IMAGE_BYTES));
    byte[] read;
    try (var input = storage.open(first.reference())) {
      read = input.readAllBytes();
    }
    read[0] = 0;

    assertThat(first).isEqualTo(second);
    assertThat(storage.objectCount()).isEqualTo(1);
    assertThat(storage.exists(first.reference())).isTrue();
    try (var input = storage.open(first.reference())) {
      assertThat(input.readAllBytes()).isEqualTo(IMAGE_BYTES);
    }
  }

  @Test
  void differentBytesProduceDifferentReferences() throws Exception {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();

    StoredMedia first = storage.store(new ByteArrayInputStream(IMAGE_BYTES));
    StoredMedia second = storage.store(
        new ByteArrayInputStream("another-image".getBytes(StandardCharsets.UTF_8))
    );

    assertThat(first.reference()).isNotEqualTo(second.reference());
    assertThat(storage.objectCount()).isEqualTo(2);
  }

  @Test
  void missingObjectIsReportedWithoutCreatingIt() {
    InMemoryStorageAdapter storage = new InMemoryStorageAdapter();
    StorageReference missing = StorageReference.fromSha256("0".repeat(64));

    assertThat(storage.exists(missing)).isFalse();
    assertThatThrownBy(() -> storage.open(missing))
        .isInstanceOf(FileNotFoundException.class);
  }
}
