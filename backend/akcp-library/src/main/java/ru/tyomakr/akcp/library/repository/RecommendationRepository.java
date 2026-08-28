package ru.tyomakr.akcp.library.repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.persistence.RecommendationAttachmentSourceRow;
import ru.tyomakr.akcp.library.persistence.RecommendationFeedbackRow;
import ru.tyomakr.akcp.library.persistence.RecommendationFeatureRow;
import ru.tyomakr.akcp.library.persistence.RecommendationItemProfileRow;
import ru.tyomakr.akcp.library.persistence.RecommendationServingEventRow;

@Repository
public class RecommendationRepository {
  private final DatabaseClient databaseClient;

  public RecommendationRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<RecommendationFeatureRow> upsertByAttachment(RecommendationFeatureRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO recommendation_image_features (
              id, dataset, attachment_id, image_url, sha256, phash, embedding_json,
              text_ratio, text_dominant, created_at, updated_at,
              analysis_version, analysis_explanation_json
            ) VALUES (
              :id, :dataset, :attachment_id, :image_url, :sha256, :phash, :embedding_json,
              :text_ratio, :text_dominant, :created_at, :updated_at,
              :analysis_version, :analysis_explanation_json
            )
            ON CONFLICT (attachment_id) WHERE attachment_id IS NOT NULL
            DO UPDATE SET
              dataset = EXCLUDED.dataset,
              image_url = EXCLUDED.image_url,
              sha256 = EXCLUDED.sha256,
              phash = EXCLUDED.phash,
              embedding_json = EXCLUDED.embedding_json,
              text_ratio = EXCLUDED.text_ratio,
              text_dominant = EXCLUDED.text_dominant,
              analysis_version = EXCLUDED.analysis_version,
              analysis_explanation_json = EXCLUDED.analysis_explanation_json,
              updated_at = EXCLUDED.updated_at
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("dataset", row.dataset())
        .bind("attachment_id", row.attachmentId())
        .bind("image_url", row.imageUrl())
        .bind("text_dominant", row.textDominant())
        .bind("created_at", row.createdAt())
        .bind("updated_at", row.updatedAt())
        .bind("analysis_version", row.analysisVersion());
    spec = bindNullable(spec, "sha256", row.sha256(), String.class);
    spec = bindNullable(spec, "phash", row.phash(), Long.class);
    spec = bindNullable(spec, "embedding_json", row.embeddingJson(), String.class);
    spec = bindNullable(spec, "text_ratio", row.textRatio(), Double.class);
    spec = bindNullable(
        spec,
        "analysis_explanation_json",
        row.analysisExplanationJson(),
        String.class
    );
    return spec.map((r, m) -> toFeatureRow(r)).one();
  }

  public Mono<RecommendationFeatureRow> upsertByDatasetAndImageUrl(RecommendationFeatureRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO recommendation_image_features (
              id, dataset, attachment_id, image_url, sha256, phash, embedding_json,
              text_ratio, text_dominant, created_at, updated_at,
              analysis_version, analysis_explanation_json
            ) VALUES (
              :id, :dataset, :attachment_id, :image_url, :sha256, :phash, :embedding_json,
              :text_ratio, :text_dominant, :created_at, :updated_at,
              :analysis_version, :analysis_explanation_json
            )
            ON CONFLICT (dataset, image_url)
            DO UPDATE SET
              attachment_id = COALESCE(EXCLUDED.attachment_id, recommendation_image_features.attachment_id),
              sha256 = EXCLUDED.sha256,
              phash = EXCLUDED.phash,
              embedding_json = EXCLUDED.embedding_json,
              text_ratio = EXCLUDED.text_ratio,
              text_dominant = EXCLUDED.text_dominant,
              analysis_version = EXCLUDED.analysis_version,
              analysis_explanation_json = EXCLUDED.analysis_explanation_json,
              updated_at = EXCLUDED.updated_at
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("dataset", row.dataset())
        .bind("image_url", row.imageUrl())
        .bind("text_dominant", row.textDominant())
        .bind("created_at", row.createdAt())
        .bind("updated_at", row.updatedAt())
        .bind("analysis_version", row.analysisVersion());
    spec = bindNullable(spec, "attachment_id", row.attachmentId(), UUID.class);
    spec = bindNullable(spec, "sha256", row.sha256(), String.class);
    spec = bindNullable(spec, "phash", row.phash(), Long.class);
    spec = bindNullable(spec, "embedding_json", row.embeddingJson(), String.class);
    spec = bindNullable(spec, "text_ratio", row.textRatio(), Double.class);
    spec = bindNullable(
        spec,
        "analysis_explanation_json",
        row.analysisExplanationJson(),
        String.class
    );
    return spec.map((r, m) -> toFeatureRow(r)).one();
  }

  public Mono<RecommendationFeatureRow> findByAttachmentId(UUID attachmentId) {
    return databaseClient.sql("""
            SELECT f.*, a.item_id
            FROM recommendation_image_features f
            LEFT JOIN attachments a ON a.id = f.attachment_id
            WHERE f.attachment_id = :attachment_id
            """)
        .bind("attachment_id", attachmentId)
        .map((r, m) -> toFeatureRow(r))
        .one();
  }

  public Mono<RecommendationFeatureRow> findByDatasetAndImageUrl(String dataset, String imageUrl) {
    return databaseClient.sql("""
            SELECT f.*, a.item_id
            FROM recommendation_image_features f
            LEFT JOIN attachments a ON a.id = f.attachment_id
            WHERE f.dataset = :dataset
              AND f.image_url = :image_url
            """)
        .bind("dataset", dataset)
        .bind("image_url", imageUrl)
        .map((r, m) -> toFeatureRow(r))
        .one();
  }

  public Flux<RecommendationFeatureRow> listCandidates(String dataset, UUID excludeAttachmentId, int poolLimit) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            SELECT f.*, a.item_id
            FROM recommendation_image_features f
            LEFT JOIN attachments a ON a.id = f.attachment_id
            WHERE f.dataset = :dataset
              AND f.attachment_id IS NOT NULL
              AND (:exclude_attachment_id IS NULL OR f.attachment_id <> :exclude_attachment_id)
            ORDER BY f.updated_at DESC
            LIMIT :pool_limit
            """)
        .bind("dataset", dataset)
        .bind("pool_limit", poolLimit);
    spec = bindNullable(spec, "exclude_attachment_id", excludeAttachmentId, UUID.class);
    return spec.map((r, m) -> toFeatureRow(r)).all();
  }

  public Flux<RecommendationFeatureRow> listDatasetNonTextDominant(String dataset, int poolLimit) {
    return databaseClient.sql("""
            SELECT f.*, a.item_id
            FROM recommendation_image_features f
            LEFT JOIN attachments a ON a.id = f.attachment_id
            WHERE f.dataset = :dataset
              AND f.text_dominant = FALSE
            ORDER BY f.updated_at DESC
            LIMIT :pool_limit
            """)
        .bind("dataset", dataset)
        .bind("pool_limit", poolLimit)
        .map((r, m) -> toFeatureRow(r))
        .all();
  }

  public Mono<RecommendationFeedbackRow> insertFeedback(RecommendationFeedbackRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO recommendation_feedback (
              id, username, reference_attachment_id, recommended_attachment_id, action, reason,
              serving_event_id, served_rank, note, created_at
            ) VALUES (
              :id, :username, :reference_attachment_id, :recommended_attachment_id, :action, :reason,
              :serving_event_id, :served_rank, :note, :created_at
            )
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("username", row.username())
        .bind("action", row.action())
        .bind("created_at", row.createdAt());
    spec = bindNullable(spec, "reference_attachment_id", row.referenceAttachmentId(), UUID.class);
    spec = bindNullable(spec, "recommended_attachment_id", row.recommendedAttachmentId(), UUID.class);
    spec = bindNullable(spec, "reason", row.reason(), String.class);
    spec = bindNullable(spec, "serving_event_id", row.servingEventId(), UUID.class);
    spec = bindNullable(spec, "served_rank", row.servedRank(), Integer.class);
    spec = bindNullable(spec, "note", row.note(), String.class);
    return spec.map((r, m) -> new RecommendationFeedbackRow(
        r.get("id", UUID.class),
        r.get("username", String.class),
        r.get("reference_attachment_id", UUID.class),
        r.get("recommended_attachment_id", UUID.class),
        r.get("action", String.class),
        r.get("reason", String.class),
        r.get("serving_event_id", UUID.class),
        r.get("served_rank", Integer.class),
        r.get("note", String.class),
        r.get("created_at", Instant.class)
    )).one();
  }

  public Flux<RecommendationAttachmentSourceRow> listAttachmentsWithoutFeatures(int limit) {
    return databaseClient.sql("""
            SELECT
              a.id AS attachment_id,
              a.item_id,
              a.url AS image_url,
              i.source_type,
              i.created_at AS item_created_at,
              COALESCE(string_agg(DISTINCT t.name, ','), '') AS tags_csv,
              analysis.input_sha256 AS analysis_sha256,
              analysis.phash AS analysis_phash,
              analysis.embedding_json AS analysis_embedding_json,
              analysis.text_ratio AS analysis_text_ratio,
              analysis.text_dominant AS analysis_text_dominant,
              analysis.analysis_version,
              analysis.explanation_json AS analysis_explanation_json
            FROM attachments a
            JOIN items i ON i.id = a.item_id
            LEFT JOIN item_tags it ON it.item_id = i.id
            LEFT JOIN tags t ON t.id = it.tag_id
            LEFT JOIN media_assets ma ON ma.attachment_id = a.id
            LEFT JOIN LATERAL (
              SELECT mar.input_sha256, mar.phash, mar.embedding_json,
                     mar.text_ratio, mar.text_dominant, mar.analysis_version,
                     mar.explanation_json
              FROM media_analysis_runs mar
              WHERE mar.asset_id = ma.id
              ORDER BY mar.created_at DESC, mar.id DESC
              LIMIT 1
            ) analysis ON TRUE
            LEFT JOIN recommendation_image_features f ON f.attachment_id = a.id
            WHERE a.type = 'IMAGE'
              AND (
                f.attachment_id IS NULL
                OR f.analysis_version IS NULL
                OR f.analysis_version = 'legacy-url-v1'
              )
            GROUP BY a.id, a.item_id, a.url, i.source_type, i.created_at,
                     analysis.input_sha256, analysis.phash, analysis.embedding_json,
                     analysis.text_ratio, analysis.text_dominant,
                     analysis.analysis_version, analysis.explanation_json
            ORDER BY i.created_at DESC, a.id DESC
            LIMIT :limit
            """)
        .bind("limit", limit)
        .map((row, metadata) -> new RecommendationAttachmentSourceRow(
            row.get("attachment_id", UUID.class),
            row.get("item_id", UUID.class),
            row.get("image_url", String.class),
            row.get("source_type", String.class),
            row.get("item_created_at", Instant.class),
            splitTags(row.get("tags_csv", String.class)),
            row.get("analysis_sha256", String.class),
            row.get("analysis_phash", Long.class),
            row.get("analysis_embedding_json", String.class),
            row.get("analysis_text_ratio", Double.class),
            row.get("analysis_text_dominant", Boolean.class),
            row.get("analysis_version", String.class),
            row.get("analysis_explanation_json", String.class)
        ))
        .all();
  }

  public Mono<RecommendationItemProfileRow> findItemProfileByAttachmentId(UUID attachmentId) {
    return databaseClient.sql("""
            SELECT
              a.id AS attachment_id,
              a.item_id,
              i.source_type,
              i.created_at AS item_created_at,
              COALESCE(string_agg(DISTINCT t.name, ','), '') AS tags_csv
            FROM attachments a
            JOIN items i ON i.id = a.item_id
            LEFT JOIN item_tags it ON it.item_id = i.id
            LEFT JOIN tags t ON t.id = it.tag_id
            WHERE a.id = :attachment_id
            GROUP BY a.id, a.item_id, i.source_type, i.created_at
            """)
        .bind("attachment_id", attachmentId)
        .map((row, metadata) -> new RecommendationItemProfileRow(
            row.get("attachment_id", UUID.class),
            row.get("item_id", UUID.class),
            row.get("source_type", String.class),
            row.get("item_created_at", Instant.class),
            splitTags(row.get("tags_csv", String.class))
        ))
        .one();
  }

  public Flux<RecommendationItemProfileRow> listItemProfilesByAttachmentIds(List<UUID> attachmentIds) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return Flux.empty();
    }
    return databaseClient.sql("""
            SELECT
              a.id AS attachment_id,
              a.item_id,
              i.source_type,
              i.created_at AS item_created_at,
              COALESCE(string_agg(DISTINCT t.name, ','), '') AS tags_csv
            FROM attachments a
            JOIN items i ON i.id = a.item_id
            LEFT JOIN item_tags it ON it.item_id = i.id
            LEFT JOIN tags t ON t.id = it.tag_id
            WHERE a.id = ANY(:attachment_ids)
            GROUP BY a.id, a.item_id, i.source_type, i.created_at
            """)
        .bind("attachment_ids", attachmentIds.toArray(new UUID[0]))
        .map((row, metadata) -> new RecommendationItemProfileRow(
            row.get("attachment_id", UUID.class),
            row.get("item_id", UUID.class),
            row.get("source_type", String.class),
            row.get("item_created_at", Instant.class),
            splitTags(row.get("tags_csv", String.class))
        ))
        .all();
  }

  public Mono<RecommendationServingEventRow> insertServingEvent(RecommendationServingEventRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO recommendation_serving_events (
              id,
              username,
              reference_attachment_id,
              experiment_group,
              requested_limit,
              returned_count,
              candidates_json,
              latency_ms,
              created_at
            ) VALUES (
              :id,
              :username,
              :reference_attachment_id,
              :experiment_group,
              :requested_limit,
              :returned_count,
              :candidates_json,
              :latency_ms,
              :created_at
            )
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("username", row.username())
        .bind("reference_attachment_id", row.referenceAttachmentId())
        .bind("experiment_group", row.experimentGroup())
        .bind("requested_limit", row.requestedLimit())
        .bind("returned_count", row.returnedCount())
        .bind("created_at", row.createdAt());
    spec = bindNullable(spec, "candidates_json", row.candidatesJson(), String.class);
    spec = bindNullable(spec, "latency_ms", row.latencyMs(), Long.class);
    return spec.map((r, m) -> new RecommendationServingEventRow(
        r.get("id", UUID.class),
        r.get("username", String.class),
        r.get("reference_attachment_id", UUID.class),
        r.get("experiment_group", String.class),
        safeGetInt(r, "requested_limit"),
        safeGetInt(r, "returned_count"),
        r.get("candidates_json", String.class),
        r.get("latency_ms", Long.class),
        r.get("created_at", Instant.class)
    )).one();
  }

  public Mono<RecommendationServingEventRow> findServingEvent(UUID id, String username) {
    return databaseClient.sql("""
            SELECT *
            FROM recommendation_serving_events
            WHERE id = :id
              AND username = :username
            """)
        .bind("id", id)
        .bind("username", username)
        .map((row, metadata) -> new RecommendationServingEventRow(
            row.get("id", UUID.class),
            row.get("username", String.class),
            row.get("reference_attachment_id", UUID.class),
            row.get("experiment_group", String.class),
            safeGetInt(row, "requested_limit"),
            safeGetInt(row, "returned_count"),
            row.get("candidates_json", String.class),
            row.get("latency_ms", Long.class),
            row.get("created_at", Instant.class)
        ))
        .one();
  }

  private RecommendationFeatureRow toFeatureRow(io.r2dbc.spi.Readable row) {
    return new RecommendationFeatureRow(
        row.get("id", UUID.class),
        row.get("dataset", String.class),
        row.get("attachment_id", UUID.class),
        safeGet(row, "item_id", UUID.class),
        row.get("image_url", String.class),
        row.get("sha256", String.class),
        row.get("phash", Long.class),
        row.get("embedding_json", String.class),
        row.get("text_ratio", Double.class),
        Boolean.TRUE.equals(row.get("text_dominant", Boolean.class)),
        row.get("created_at", Instant.class),
        row.get("updated_at", Instant.class),
        safeGet(row, "analysis_version", String.class),
        safeGet(row, "analysis_explanation_json", String.class)
    );
  }

  private <T> T safeGet(io.r2dbc.spi.Readable row, String name, Class<T> type) {
    try {
      return row.get(name, type);
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private int safeGetInt(io.r2dbc.spi.Readable row, String name) {
    Integer value = safeGet(row, name, Integer.class);
    return value == null ? 0 : value;
  }

  private List<String> splitTags(String tagsCsv) {
    if (tagsCsv == null || tagsCsv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(tagsCsv.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
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
}
