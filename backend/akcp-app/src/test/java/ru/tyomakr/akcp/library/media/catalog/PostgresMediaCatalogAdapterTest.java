package ru.tyomakr.akcp.library.media.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.tyomakr.akcp.core.content.CatalogRegistration;
import ru.tyomakr.akcp.core.content.MediaAsset;
import ru.tyomakr.akcp.core.content.SourceOccurrence;
import ru.tyomakr.akcp.core.content.SourcePlatform;
import ru.tyomakr.akcp.core.content.StorageReference;

@Testcontainers
class PostgresMediaCatalogAdapterTest {
  private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private String schema;
  private DatabaseClient databaseClient;
  private PostgresMediaCatalogAdapter catalog;

  @BeforeEach
  void migrateFreshSchema() {
    schema = "catalog_" + UUID.randomUUID().toString().replace("-", "");
    flyway(schema).migrate();
    flyway(schema).validate();

    ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(POSTGRES.getHost())
            .port(POSTGRES.getFirstMappedPort())
            .database(POSTGRES.getDatabaseName())
            .username(POSTGRES.getUsername())
            .password(POSTGRES.getPassword())
            .schema(schema)
            .build()
    );
    databaseClient = DatabaseClient.create(connectionFactory);
    catalog = new PostgresMediaCatalogAdapter(
        databaseClient,
        TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory))
    );
  }

  @Test
  void canonicalizesConcurrentEqualContentAndRetainsBothSources() {
    MediaAsset firstCandidate = asset(UUID.randomUUID(), "a".repeat(64));
    MediaAsset secondCandidate = asset(UUID.randomUUID(), "a".repeat(64));

    List<RegistrationAttempt> attempts = runConcurrently(
        () -> catalog.register(
            firstCandidate,
            source(firstCandidate.id(), SourcePlatform.VK, "vk:photo:100")
        ),
        () -> catalog.register(
            secondCandidate,
            source(secondCandidate.id(), SourcePlatform.WEB, "web:image:100")
        )
    );
    assertThat(attempts).extracting(RegistrationAttempt::failure).containsOnlyNulls();
    List<CatalogRegistration> registrations = attempts.stream()
        .map(RegistrationAttempt::registration)
        .toList();

    assertThat(registrations).extracting(CatalogRegistration::mediaAssetCreated)
        .containsExactlyInAnyOrder(true, false);
    assertThat(registrations).extracting(registration -> registration.mediaAsset().id())
        .containsOnly(registrations.getFirst().mediaAsset().id());
    assertThat(await(catalog.findSourceOccurrences(
        registrations.getFirst().mediaAsset().id()
    ))).hasSize(2);
    assertThat(countRows("catalog_media_assets")).isEqualTo(1);
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(2);
  }

  @Test
  void concurrentEqualContentForSameSourceIsIdempotent() {
    MediaAsset firstCandidate = asset(UUID.randomUUID(), "1".repeat(64));
    MediaAsset secondCandidate = asset(UUID.randomUUID(), "1".repeat(64));

    List<RegistrationAttempt> attempts = runConcurrently(
        () -> catalog.register(
            firstCandidate,
            source(firstCandidate.id(), SourcePlatform.VK, "vk:photo:105")
        ),
        () -> catalog.register(
            secondCandidate,
            source(secondCandidate.id(), SourcePlatform.VK, "vk:photo:105")
        )
    );

    assertThat(attempts).extracting(RegistrationAttempt::failure).containsOnlyNulls();
    assertThat(attempts).extracting(
        attempt -> attempt.registration().sourceOccurrenceCreated()
    ).containsExactlyInAnyOrder(true, false);
    assertThat(countRows("catalog_media_assets")).isEqualTo(1);
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(1);
  }

  @Test
  void concurrentDifferentContentForSameSourceKeepsOnlyTheWinner() {
    MediaAsset firstCandidate = asset(UUID.randomUUID(), "2".repeat(64));
    MediaAsset secondCandidate = asset(UUID.randomUUID(), "3".repeat(64));

    List<RegistrationAttempt> attempts = runConcurrently(
        () -> catalog.register(
            firstCandidate,
            source(firstCandidate.id(), SourcePlatform.VK, "vk:photo:106")
        ),
        () -> catalog.register(
            secondCandidate,
            source(secondCandidate.id(), SourcePlatform.VK, "vk:photo:106")
        )
    );

    assertThat(attempts).filteredOn(attempt -> attempt.registration() != null).hasSize(1);
    assertThat(attempts).filteredOn(attempt ->
        attempt.failure() instanceof IllegalStateException
            && "source record already points to different content".equals(
                attempt.failure().getMessage()
            )
    ).hasSize(1);
    assertThat(countRows("catalog_media_assets")).isEqualTo(1);
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(1);
  }

  @Test
  void repeatedSourceIsIdempotentAndBlankConnectionUsesOneDatabaseScope() {
    MediaAsset candidate = asset(UUID.randomUUID(), "b".repeat(64));
    SourceOccurrence firstSource = source(
        candidate.id(),
        SourcePlatform.VK,
        "vk:photo:101"
    );
    SourceOccurrence normalizedSameSource = new SourceOccurrence(
        UUID.randomUUID(),
        candidate.id(),
        SourcePlatform.VK,
        " vk:photo:101 ",
        " ",
        null,
        null,
        null,
        "https://example.test/image.jpg",
        null,
        NOW.plusSeconds(1)
    );

    CatalogRegistration first = await(catalog.register(candidate, firstSource));
    CatalogRegistration repeated = await(catalog.register(candidate, normalizedSameSource));

    assertThat(first.sourceOccurrenceCreated()).isTrue();
    assertThat(repeated.sourceOccurrenceCreated()).isFalse();
    assertThat(repeated.sourceOccurrence()).isEqualTo(first.sourceOccurrence());
    assertThat(repeated.sourceOccurrence().sourceConnectionId()).isNull();
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(1);
  }

  @Test
  void changedSourceContentRollsBackTheUnreferencedDatabaseAsset() {
    MediaAsset original = asset(UUID.randomUUID(), "c".repeat(64));
    SourceOccurrence originalSource = source(
        original.id(),
        SourcePlatform.VK,
        "vk:photo:102"
    );
    await(catalog.register(original, originalSource));
    MediaAsset changed = asset(UUID.randomUUID(), "d".repeat(64));

    assertThatThrownBy(() -> await(catalog.register(
        changed,
        source(changed.id(), SourcePlatform.VK, "vk:photo:102")
    )))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("source record already points to different content");
    assertThat(countRows("catalog_media_assets")).isEqualTo(1);
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(1);
  }

  @Test
  void rejectsCandidateIdAndCanonicalMetadataConflictsWithoutChangingCatalog() {
    MediaAsset first = asset(UUID.randomUUID(), "e".repeat(64));
    MediaAsset second = asset(UUID.randomUUID(), "f".repeat(64));
    await(catalog.register(
        first,
        source(first.id(), SourcePlatform.VK, "vk:photo:103")
    ));
    await(catalog.register(
        second,
        source(second.id(), SourcePlatform.WEB, "web:image:103")
    ));
    MediaAsset candidateIdCollision = asset(second.id(), first.sha256());

    assertThatThrownBy(() -> await(catalog.register(
        candidateIdCollision,
        source(
            candidateIdCollision.id(),
            SourcePlatform.MANUAL,
            "manual:image:103"
        )
    )))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("media asset ID is already registered with different content");

    MediaAsset metadataConflict = new MediaAsset(
        UUID.randomUUID(),
        first.sha256(),
        first.mimeType(),
        first.width() + 1,
        first.height(),
        first.storageReference()
    );
    assertThatThrownBy(() -> await(catalog.register(
        metadataConflict,
        source(metadataConflict.id(), SourcePlatform.WEB, "web:image:104")
    )))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("content metadata conflicts with canonical media asset");
    assertThat(countRows("catalog_media_assets")).isEqualTo(2);
    assertThat(countRows("catalog_source_occurrences")).isEqualTo(2);
  }

  @Test
  void migrationUpgradesExistingSchemaWithoutChangingLegacyRows() throws Exception {
    String upgradeSchema = "catalog_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    flyway(upgradeSchema, "10").migrate();
    UUID legacyItemId = UUID.randomUUID();
    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO %s.items
            (id, title, content, source_type, source_url, created_at, updated_at)
          VALUES
            ('%s', 'legacy fixture', NULL, 'TEST', 'fixture://legacy', NOW(), NOW())
          """.formatted(upgradeSchema, legacyItemId));
    }

    Flyway upgraded = flyway(upgradeSchema);
    upgraded.migrate();
    upgraded.validate();

    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
             SELECT title,
                    to_regclass('%s.catalog_media_assets') AS assets_table,
                    to_regclass('%s.catalog_source_occurrences') AS sources_table
             FROM %s.items
             WHERE id = '%s'
             """.formatted(upgradeSchema, upgradeSchema, upgradeSchema, legacyItemId))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("title")).isEqualTo("legacy fixture");
      assertThat(result.getString("assets_table")).isNotNull();
      assertThat(result.getString("sources_table")).isNotNull();
    }
  }

  private Flyway flyway(String targetSchema) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(targetSchema)
        .defaultSchema(targetSchema)
        .load();
  }

  private Flyway flyway(String targetSchema, String targetVersion) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(targetSchema)
        .defaultSchema(targetSchema)
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

  private long countRows(String table) {
    return await(databaseClient.sql("SELECT COUNT(*) AS count FROM " + table)
        .map((row, metadata) -> row.get("count", Long.class))
        .one()
        .toFuture());
  }

  private List<RegistrationAttempt> runConcurrently(
      Supplier<CompletionStage<CatalogRegistration>> first,
      Supplier<CompletionStage<CatalogRegistration>> second
  ) {
    CyclicBarrier startBarrier = new CyclicBarrier(2);
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      CompletableFuture<RegistrationAttempt> firstStage = CompletableFuture.supplyAsync(
          () -> attemptAfterBarrier(startBarrier, first),
          workers
      );
      CompletableFuture<RegistrationAttempt> secondStage = CompletableFuture.supplyAsync(
          () -> attemptAfterBarrier(startBarrier, second),
          workers
      );
      return List.of(await(firstStage), await(secondStage));
    }
  }

  private RegistrationAttempt attemptAfterBarrier(
      CyclicBarrier startBarrier,
      Supplier<CompletionStage<CatalogRegistration>> registration
  ) {
    try {
      startBarrier.await();
      return new RegistrationAttempt(await(registration.get()), null);
    } catch (Throwable failure) {
      return new RegistrationAttempt(null, failure);
    }
  }

  private static MediaAsset asset(UUID id, String sha256) {
    return new MediaAsset(
        id,
        sha256,
        "image/jpeg",
        1280,
        720,
        StorageReference.fromSha256(sha256)
    );
  }

  private static SourceOccurrence source(
      UUID proposedMediaAssetId,
      SourcePlatform platform,
      String sourceRecordId
  ) {
    return new SourceOccurrence(
        UUID.randomUUID(),
        proposedMediaAssetId,
        platform,
        sourceRecordId,
        null,
        null,
        null,
        null,
        "https://example.test/image.jpg",
        null,
        NOW
    );
  }

  private static <T> T await(CompletionStage<T> stage) {
    try {
      return stage.toCompletableFuture().join();
    } catch (CompletionException exception) {
      if (exception.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw exception;
    }
  }

  private record RegistrationAttempt(
      CatalogRegistration registration,
      Throwable failure
  ) {
  }
}
