package ru.tyomakr.akcp.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.r2dbc.core.DatabaseClient;
import ru.tyomakr.akcp.library.persistence.RecommendationAttachmentSourceRow;
import ru.tyomakr.akcp.library.dto.RecommendationFeedbackRequest;
import ru.tyomakr.akcp.library.config.RecommendationProperties;
import ru.tyomakr.akcp.library.repository.RecommendationRepository;
import ru.tyomakr.akcp.library.service.RecommendationDataset;
import ru.tyomakr.akcp.library.service.RecommendationFeatureExtractor;
import ru.tyomakr.akcp.library.service.RecommendationService;

@Testcontainers
class RecommendationRepositoryPostgresIT {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static RecommendationRepository repository;
  private static RecommendationFeatureExtractor extractor;

  @BeforeAll
  static void setUpSchema() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    ConnectionFactory connectionFactory = ConnectionFactories.get(
        String.format(
            "r2dbc:postgresql://%s:%d/%s?user=%s&password=%s",
            POSTGRES.getHost(),
            POSTGRES.getFirstMappedPort(),
            POSTGRES.getDatabaseName(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        )
    );
    repository = new RecommendationRepository(DatabaseClient.create(connectionFactory));
    extractor = new RecommendationFeatureExtractor();
  }

  @Test
  void backfillReadsPersistedAnalysisAndRetriesLegacyRowsOnly() throws Exception {
    UUID itemId = UUID.randomUUID();
    UUID attachmentId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    UUID analysisId = UUID.randomUUID();
    String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    try (Connection connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO items (id, title, content, source_type, source_url, created_at, updated_at)
          VALUES ('%s', 'analysis fixture', NULL, 'VK', 'fixture://item', '%s', '%s')
          """.formatted(itemId, now, now));
      statement.executeUpdate("""
          INSERT INTO attachments (id, item_id, type, url, metadata)
          VALUES ('%s', '%s', 'IMAGE', 'fixture://image', NULL)
          """.formatted(attachmentId, itemId));
      statement.executeUpdate("""
          INSERT INTO media_assets (
            id, attachment_id, source_kind, external_media_id, published,
            mime_type, byte_size, sha256, content_bytes, rights_basis,
            provenance_json, imported_at
          ) VALUES (
            '%s', '%s', 'VK_AUTHORIZED_EXPORT', '%s', TRUE,
            'image/png', 3, '%s', decode('010203', 'hex'), 'authorized fixture',
            '{"source":"integration-fixture"}', '%s'
          )
          """.formatted(assetId, attachmentId, assetId, sha256, now));
      statement.executeUpdate("""
          INSERT INTO media_analysis_runs (
            id, asset_id, input_sha256, analysis_version,
            hash_provider_version, phash_provider_version,
            text_provider_version, embedding_provider_version,
            phash, embedding_json, text_ratio, text_role,
            text_dominant, explanation_json, created_at
          ) VALUES (
            '%s', '%s', '%s', 'task6-local-pixel-v1',
            'hash-v1', 'phash-v1', 'text-v1', 'embedding-v1',
            7, '[0.1,0.2]', 0.2, 'ACCENT', FALSE,
            '{"provider":"integration-fixture"}', '%s'
          )
          """.formatted(analysisId, assetId, sha256, now));
    }

    List<RecommendationAttachmentSourceRow> first = repository
        .listAttachmentsWithoutFeatures(10)
        .collectList()
        .block();
    assertThat(first).hasSize(1);
    var extracted = extractor.extract(first.get(0), RecommendationDataset.CANDIDATE, now);
    assertThat(extracted.sha256()).isEqualTo(sha256);
    assertThat(extracted.phash()).isEqualTo(7L);
    assertThat(extracted.embeddingJson()).isEqualTo("[0.1,0.2]");
    assertThat(extracted.textRatio()).isEqualTo(0.2d);
    assertThat(extracted.analysisVersion()).isEqualTo("task6-local-pixel-v1");

    UUID featureId = UUID.randomUUID();
    try (Connection connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO recommendation_image_features (
            id, dataset, attachment_id, image_url, sha256, phash,
            embedding_json, text_ratio, text_dominant, created_at, updated_at,
            analysis_version, analysis_explanation_json
          ) VALUES (
            '%s', 'CANDIDATE', '%s', 'fixture://image', '%s', 7,
            '[0.1,0.2]', 0.2, FALSE, '%s', '%s',
            'task6-local-pixel-v1', '{"provider":"integration-fixture"}'
          )
          """.formatted(featureId, attachmentId, sha256, now, now));
    }

    List<RecommendationAttachmentSourceRow> versioned = repository
        .listAttachmentsWithoutFeatures(10)
        .collectList()
        .block();
    assertThat(versioned).isEmpty();

    try (Connection connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          UPDATE recommendation_image_features
          SET analysis_version = 'legacy-url-v1'
          WHERE id = '%s'
          """.formatted(featureId));
    }
    assertThat(repository.listAttachmentsWithoutFeatures(10).collectList().block()).hasSize(1);

    UUID candidateItemId = UUID.randomUUID();
    UUID candidateAttachmentId = UUID.randomUUID();
    UUID candidateFeatureId = UUID.randomUUID();
    UUID historyItemId = UUID.randomUUID();
    UUID historyAttachmentId = UUID.randomUUID();
    UUID historyFeatureId = UUID.randomUUID();
    String candidateSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    String historySha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    try (Connection connection = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
         Statement statement = connection.createStatement()) {
      insertItemAndAttachment(statement, candidateItemId, candidateAttachmentId, "fixture://candidate", now);
      insertItemAndAttachment(statement, historyItemId, historyAttachmentId, "fixture://history", now);
      statement.executeUpdate("""
          UPDATE recommendation_image_features
          SET dataset = 'CANDIDATE',
              analysis_version = 'task6-local-pixel-v1',
              analysis_explanation_json = '{"provider":"integration-fixture"}'
          WHERE id = '%s'
          """.formatted(featureId));
      insertFeature(statement, candidateFeatureId, "CANDIDATE", candidateAttachmentId, candidateSha, 8L, now);
      insertFeature(statement, historyFeatureId, "VK_WALL", historyAttachmentId, historySha, 65535L, now);
    }

    RecommendationService service = new RecommendationService(
        repository,
        extractor,
        new RecommendationProperties(),
        new ObjectMapper(),
        new SimpleMeterRegistry()
    );
    var top = service.topRecommendations("fixture-user", attachmentId, 5).block();
    assertThat(top).isNotNull();
    assertThat(top.returnedCount()).isEqualTo(1);
    assertThat(top.candidates().get(0).attachmentId()).isEqualTo(candidateAttachmentId);

    var feedback = service.saveFeedback(
        "fixture-user",
        new RecommendationFeedbackRequest(
            attachmentId,
            candidateAttachmentId,
            "APPROVE",
            "GOOD_FIT",
            top.runId(),
            1,
            "fixture accepted"
        )
    ).block();
    assertThat(feedback).isNotNull();
    assertThat(feedback.servingEventId()).isEqualTo(top.runId());
    assertThat(feedback.servedRank()).isEqualTo(1);
  }

  private void insertItemAndAttachment(
      Statement statement,
      UUID itemId,
      UUID attachmentId,
      String imageUrl,
      Instant now
  ) throws Exception {
    statement.executeUpdate("""
        INSERT INTO items (id, title, content, source_type, source_url, created_at, updated_at)
        VALUES ('%s', 'recommendation fixture', NULL, 'VK', 'fixture://item', '%s', '%s')
        """.formatted(itemId, now, now));
    statement.executeUpdate("""
        INSERT INTO attachments (id, item_id, type, url, metadata)
        VALUES ('%s', '%s', 'IMAGE', '%s', NULL)
        """.formatted(attachmentId, itemId, imageUrl));
  }

  private void insertFeature(
      Statement statement,
      UUID featureId,
      String dataset,
      UUID attachmentId,
      String sha256,
      long phash,
      Instant now
  ) throws Exception {
    statement.executeUpdate("""
        INSERT INTO recommendation_image_features (
          id, dataset, attachment_id, image_url, sha256, phash,
          embedding_json, text_ratio, text_dominant, created_at, updated_at,
          analysis_version, analysis_explanation_json
        ) VALUES (
          '%s', '%s', '%s', 'fixture://feature-%s', '%s', %d,
          '[0.1,0.2]', 0.2, FALSE, '%s', '%s',
          'task6-local-pixel-v1', '{"provider":"integration-fixture"}'
        )
        """.formatted(featureId, dataset, attachmentId, featureId, sha256, phash, now, now));
  }
}
