package ru.tyomakr.akcp.library.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.SavedSelection;

@Repository
public class SavedSelectionRepository {
  private final DatabaseClient databaseClient;

  public SavedSelectionRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<Void> save(SavedSelection selection, String attachmentIdsText) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO saved_selections (
              id, username, item_id, attachment_ids, target, created_at, expires_at
            ) VALUES (
              :id, :username, :item_id, :attachment_ids, :target, :created_at, :expires_at
            )
            """)
        .bind("id", selection.id())
        .bind("username", selection.username())
        .bind("item_id", selection.itemId())
        .bind("attachment_ids", attachmentIdsText)
        .bind("created_at", selection.createdAt())
        .bind("expires_at", selection.expiresAt());
    spec = bindNullable(spec, "target", selection.target(), String.class);
    return spec.then();
  }

  public Flux<SavedSelectionRow> findByUser(String username, Instant now) {
    return databaseClient.sql("""
            SELECT id, username, item_id, attachment_ids, target, created_at, expires_at
            FROM saved_selections
            WHERE username = :username AND expires_at > :now
            ORDER BY created_at DESC
            """)
        .bind("username", username)
        .bind("now", now)
        .map((row, metadata) -> new SavedSelectionRow(
            row.get("id", UUID.class),
            row.get("username", String.class),
            row.get("item_id", UUID.class),
            row.get("attachment_ids", String.class),
            row.get("target", String.class),
            toInstant(row.get("created_at", OffsetDateTime.class)),
            toInstant(row.get("expires_at", OffsetDateTime.class))
        ))
        .all();
  }

  public Mono<Void> deleteExpired(Instant now) {
    return databaseClient.sql("DELETE FROM saved_selections WHERE expires_at <= :now")
        .bind("now", now)
        .then();
  }

  public Mono<Void> deleteById(UUID id, String username) {
    return databaseClient.sql("DELETE FROM saved_selections WHERE id = :id AND username = :username")
        .bind("id", id)
        .bind("username", username)
        .then();
  }

  private static Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private <T> DatabaseClient.GenericExecuteSpec bindNullable(
      DatabaseClient.GenericExecuteSpec spec,
      String name,
      T value,
      Class<T> type
  ) {
    if (value == null) {
      return spec.bindNull(name, type);
    }
    return spec.bind(name, value);
  }

  public record SavedSelectionRow(
      UUID id,
      String username,
      UUID itemId,
      String attachmentIdsText,
      String target,
      Instant createdAt,
      Instant expiresAt
  ) {
  }
}
