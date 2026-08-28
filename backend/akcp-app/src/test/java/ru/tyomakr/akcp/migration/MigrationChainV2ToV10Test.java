package ru.tyomakr.akcp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MigrationChainV2ToV10Test {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void upgradesSeededV1SchemaToV10WithoutChangingLegacyRows() throws Exception {
    String schema = schemaName("upgrade");
    flyway(schema, "1").migrate();
    UUID itemId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO %s.items
            (id, title, content, source_type, source_url, created_at, updated_at)
          VALUES
            ('%s', 'legacy item', NULL, 'TEST', 'fixture://item', NOW(), NOW())
          """.formatted(schema, itemId));
      statement.executeUpdate("""
          INSERT INTO %s.jobs
            (id, type, status, payload, created_at, updated_at, last_error)
          VALUES
            ('%s', 'PUBLISH_VK', 'IN_PROGRESS', '{}', NOW(), NOW(), 'legacy error')
          """.formatted(schema, jobId));
    }

    Flyway upgraded = flyway(schema, "10");
    upgraded.migrate();
    upgraded.validate();

    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
             SELECT i.title, j.status, j.last_error, j.attempt_count,
                    j.lease_until, j.claim_token, j.external_result,
                    to_regclass('%s.media_assets') AS media_assets_table,
                    to_regclass('%s.recommendation_serving_events') AS serving_events_table
             FROM %s.items i
             JOIN %s.jobs j ON j.id = '%s'
             WHERE i.id = '%s'
             """.formatted(schema, schema, schema, schema, jobId, itemId))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("title")).isEqualTo("legacy item");
      assertThat(result.getString("status")).isEqualTo("IN_PROGRESS");
      assertThat(result.getString("last_error")).isEqualTo("legacy error");
      assertThat(result.getInt("attempt_count")).isZero();
      assertThat(result.getObject("lease_until")).isNull();
      assertThat(result.getObject("claim_token")).isNull();
      assertThat(result.getObject("external_result")).isNull();
      assertThat(result.getString("media_assets_table")).isNotNull();
      assertThat(result.getString("serving_events_table")).isNotNull();
    }
  }

  @Test
  void upgradesV9FeedbackToV10WithoutChangingDecision() throws Exception {
    String schema = schemaName("v10");
    flyway(schema, "9").migrate();
    UUID feedbackId = UUID.randomUUID();
    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO %s.recommendation_feedback
            (id, username, action, reason, created_at)
          VALUES
            ('%s', 'fixture-user', 'APPROVE', 'legacy reason', NOW())
          """.formatted(schema, feedbackId));
    }

    Flyway upgraded = flyway(schema, "10");
    upgraded.migrate();
    upgraded.validate();

    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
             SELECT action, reason, serving_event_id, served_rank, note
             FROM %s.recommendation_feedback
             WHERE id = '%s'
             """.formatted(schema, feedbackId))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("action")).isEqualTo("APPROVE");
      assertThat(result.getString("reason")).isEqualTo("legacy reason");
      assertThat(result.getObject("serving_event_id")).isNull();
      assertThat(result.getObject("served_rank")).isNull();
      assertThat(result.getObject("note")).isNull();
    }
  }

  private Flyway flyway(String schema, String targetVersion) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target(MigrationVersion.fromVersion(targetVersion))
        .load();
  }

  private Connection jdbcConnection() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword()
    );
  }

  private static String schemaName(String prefix) {
    return "migration_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
