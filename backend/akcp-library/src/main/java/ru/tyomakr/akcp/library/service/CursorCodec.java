package ru.tyomakr.akcp.library.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.tyomakr.akcp.core.model.ItemCursor;

@Component
public class CursorCodec {
  public String encode(ItemCursor cursor) {
    String raw = cursor.createdAt().toString() + "|" + cursor.id();
    return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  public ItemCursor decode(String cursor) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(cursor);
      String raw = new String(decoded, StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid cursor");
      }
      Instant createdAt = Instant.parse(parts[0]);
      UUID id = UUID.fromString(parts[1]);
      return new ItemCursor(createdAt, id);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid cursor", ex);
    }
  }
}
