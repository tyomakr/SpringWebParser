package ru.tyomakr.akcp.library.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.persistence.AttachmentRow;

@Repository
public class AttachmentRepository {
  private final DatabaseClient databaseClient;

  public AttachmentRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Flux<AttachmentRow> findByItemIds(List<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return Flux.empty();
    }
    return databaseClient.sql("SELECT * FROM attachments WHERE item_id = ANY(:ids)")
        .bind("ids", itemIds.toArray(new UUID[0]))
        .map(RowMappers::toAttachmentRow)
        .all();
  }

  public Mono<Void> insertAll(List<AttachmentRow> rows) {
    return Flux.fromIterable(rows)
        .concatMap(row -> {
          DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO attachments (id, item_id, type, url, metadata)
                VALUES (:id, :itemId, :type, :url, :metadata)
                """)
              .bind("id", row.id())
              .bind("itemId", row.itemId())
              .bind("type", row.type())
              .bind("url", row.url());
          if (row.metadata() == null) {
            spec = spec.bindNull("metadata", String.class);
          } else {
            spec = spec.bind("metadata", row.metadata());
          }
          return spec.then();
        })
        .then();
  }
}
