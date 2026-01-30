package ru.tyomakr.akcp.library.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.persistence.ItemTagRow;
import ru.tyomakr.akcp.library.persistence.TagRow;

@Repository
public class TagRepository {
  private final DatabaseClient databaseClient;

  public TagRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<TagRow> findByName(String name) {
    return databaseClient.sql("SELECT * FROM tags WHERE name = :name")
        .bind("name", name)
        .map(RowMappers::toTagRow)
        .one();
  }

  public Mono<TagRow> insert(TagRow row) {
    return databaseClient.sql("""
            INSERT INTO tags (id, name)
            VALUES (:id, :name)
            ON CONFLICT (name) DO NOTHING
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("name", row.name())
        .map(RowMappers::toTagRow)
        .one();
  }

  public Flux<ItemTagRow> findByItemIds(List<UUID> itemIds) {
    if (itemIds.isEmpty()) {
      return Flux.empty();
    }
    return databaseClient.sql("""
            SELECT it.item_id, t.id as tag_id, t.name
            FROM item_tags it
            JOIN tags t ON t.id = it.tag_id
            WHERE it.item_id = ANY(:ids)
            """)
        .bind("ids", itemIds.toArray(new UUID[0]))
        .map(RowMappers::toItemTagRow)
        .all();
  }

  public Mono<Void> insertItemTags(UUID itemId, List<UUID> tagIds) {
    return Flux.fromIterable(tagIds)
        .concatMap(tagId -> databaseClient.sql("""
                INSERT INTO item_tags (item_id, tag_id)
                VALUES (:itemId, :tagId)
                ON CONFLICT DO NOTHING
                """)
            .bind("itemId", itemId)
            .bind("tagId", tagId)
            .then())
        .then();
  }

  public Mono<Void> deleteItemTags(UUID itemId, List<UUID> tagIds) {
    if (tagIds.isEmpty()) {
      return Mono.empty();
    }
    return databaseClient.sql("DELETE FROM item_tags WHERE item_id = :itemId AND tag_id = ANY(:tagIds)")
        .bind("itemId", itemId)
        .bind("tagIds", tagIds.toArray(new UUID[0]))
        .then();
  }
}
