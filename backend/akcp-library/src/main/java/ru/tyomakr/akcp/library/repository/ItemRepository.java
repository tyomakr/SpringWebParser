package ru.tyomakr.akcp.library.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.ItemCursor;
import ru.tyomakr.akcp.library.persistence.ItemRow;

@Repository
public class ItemRepository {
  private final DatabaseClient databaseClient;

  public ItemRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<ItemRow> insert(ItemRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO items (id, title, content, source_type, source_url, created_at, updated_at)
            VALUES (:id, :title, :content, :sourceType, :sourceUrl, :createdAt, :updatedAt)
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("title", row.title())
        .bind("sourceType", row.sourceType())
        .bind("createdAt", row.createdAt())
        .bind("updatedAt", row.updatedAt());
    spec = bindNullable(spec, "content", row.content(), String.class);
    spec = bindNullable(spec, "sourceUrl", row.sourceUrl(), String.class);

    return spec.map(RowMappers::toItemRow).one();
  }

  public Mono<ItemRow> findById(UUID id) {
    return databaseClient.sql("SELECT * FROM items WHERE id = :id")
        .bind("id", id)
        .map(RowMappers::toItemRow)
        .one();
  }

  public Flux<ItemRow> list(Instant from, Instant to, String tag, int limit, ItemCursor cursor) {
    StringBuilder sql = new StringBuilder("SELECT i.* FROM items i ");
    if (tag != null) {
      sql.append("JOIN item_tags it ON it.item_id = i.id ");
      sql.append("JOIN tags t ON t.id = it.tag_id ");
    }
    sql.append("WHERE 1=1 ");
    if (tag != null) {
      sql.append("AND t.name = :tag ");
    }
    if (from != null) {
      sql.append("AND i.created_at >= :from ");
    }
    if (to != null) {
      sql.append("AND i.created_at <= :to ");
    }
    if (cursor != null) {
      sql.append("AND (i.created_at < :cursorCreatedAt OR (i.created_at = :cursorCreatedAt AND i.id < :cursorId)) ");
    }
    sql.append("ORDER BY i.created_at DESC, i.id DESC ");
    sql.append("LIMIT :limit");

    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
        .bind("limit", limit);
    if (tag != null) {
      spec = spec.bind("tag", tag);
    }
    if (from != null) {
      spec = spec.bind("from", from);
    }
    if (to != null) {
      spec = spec.bind("to", to);
    }
    if (cursor != null) {
      spec = spec.bind("cursorCreatedAt", cursor.createdAt());
      spec = spec.bind("cursorId", cursor.id());
    }

    return spec.map(RowMappers::toItemRow).all();
  }

  public Mono<Void> updateUpdatedAt(UUID id, Instant updatedAt) {
    return databaseClient.sql("UPDATE items SET updated_at = :updatedAt WHERE id = :id")
        .bind("updatedAt", updatedAt)
        .bind("id", id)
        .then();
  }

  private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
    if (value == null) {
      return spec.bindNull(name, type);
    }
    return spec.bind(name, value);
  }
}
