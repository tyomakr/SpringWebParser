package ru.tyomakr.akcp.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.core.model.ItemCursor;

class CursorCodecTest {
  private final CursorCodec codec = new CursorCodec();

  @Test
  void roundTrip() {
    ItemCursor cursor = new ItemCursor(Instant.parse("2024-01-01T10:15:30Z"), UUID.randomUUID());
    String encoded = codec.encode(cursor);
    ItemCursor decoded = codec.decode(encoded);

    assertThat(decoded).isEqualTo(cursor);
  }
}
