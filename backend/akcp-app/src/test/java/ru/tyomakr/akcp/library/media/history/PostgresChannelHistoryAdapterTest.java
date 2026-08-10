package ru.tyomakr.akcp.library.media.history;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
import ru.tyomakr.akcp.core.content.ChannelEligibilityDecision;
import ru.tyomakr.akcp.core.content.ChannelHistoryTransition;
import ru.tyomakr.akcp.core.content.ChannelHistoryTransitionService;
import ru.tyomakr.akcp.core.content.ChannelProfile;
import ru.tyomakr.akcp.core.content.EligibilityDecisionType;
import ru.tyomakr.akcp.core.content.EligibilityReason;
import ru.tyomakr.akcp.core.content.HistoryMembershipState;
import ru.tyomakr.akcp.core.content.HistoryTransitionOutcome;
import ru.tyomakr.akcp.core.content.PublicationEvidence;
import ru.tyomakr.akcp.core.content.PublicationEvidenceSource;
import ru.tyomakr.akcp.core.content.PublicationEvidenceStatus;
import ru.tyomakr.akcp.core.content.PublicationOccurrence;
import ru.tyomakr.akcp.core.content.PublicationPlatform;
import ru.tyomakr.akcp.core.content.StorageReference;

@Testcontainers
class PostgresChannelHistoryAdapterTest {
  private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private String schema;
  private DatabaseClient databaseClient;
  private PostgresChannelHistoryAdapter history;

  @BeforeEach
  void migrateFreshSchema() {
    schema = "history_" + UUID.randomUUID().toString().replace("-", "");
    Flyway flyway = flyway(schema);
    flyway.migrate();
    flyway.validate();

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
    history = new PostgresChannelHistoryAdapter(
        databaseClient,
        TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)),
        new ChannelHistoryTransitionService()
    );
  }

  @Test
  void confirmedPublicationPromotesOnceWhileFailedAndUnknownNeverPromote() {
    UUID assetId = insertAsset("a".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:1");

    ChannelHistoryTransition failed = await(history.recordPublicationEvidence(
        unconfirmed(PublicationEvidenceStatus.FAILED, assetId, channel.id())
    ));
    ChannelHistoryTransition unknown = await(history.recordPublicationEvidence(
        unconfirmed(PublicationEvidenceStatus.UNKNOWN, assetId, channel.id())
    ));
    PublicationEvidence confirmed = confirmed(assetId, channel.id(), "vk:post:1");
    ChannelHistoryTransition first = await(history.recordPublicationEvidence(confirmed));
    ChannelHistoryTransition repeated = await(history.recordPublicationEvidence(confirmed));

    assertThat(failed.outcome()).isEqualTo(HistoryTransitionOutcome.IGNORE_UNCONFIRMED);
    assertThat(unknown.outcome()).isEqualTo(HistoryTransitionOutcome.IGNORE_UNCONFIRMED);
    assertThat(first.outcome()).isEqualTo(HistoryTransitionOutcome.PROMOTE);
    assertThat(repeated.outcome()).isEqualTo(HistoryTransitionOutcome.KEEP_ACTIVE);
    assertThat(await(history.findMembershipState(assetId, channel.id())))
        .isEqualTo(HistoryMembershipState.ACTIVE);
    assertThat(countRows("publication_occurrences")).isEqualTo(1);
    assertThat(countRows("channel_history_memberships")).isEqualTo(1);
  }

  @Test
  void exclusionIsChannelScopedAndLaterAllowReactivatesMembership() {
    UUID assetId = insertAsset("b".repeat(64));
    ChannelProfile vk = registerChannel(PublicationPlatform.VK, "vk:group:2");
    ChannelProfile telegram = registerChannel(
        PublicationPlatform.TELEGRAM,
        "telegram:channel:2"
    );
    await(history.recordPublicationEvidence(confirmed(assetId, vk.id(), "vk:post:2")));
    await(history.recordPublicationEvidence(
        confirmed(assetId, telegram.id(), "telegram:message:2")
    ));
    ChannelEligibilityDecision exclusion = decision(
        assetId,
        vk.id(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.TEXT_DOMINANT,
        null,
        NOW.plusSeconds(1)
    );

    Optional<ChannelHistoryTransition> excluded = await(
        history.recordEligibilityDecision(exclusion)
    );
    ChannelEligibilityDecision allow = decision(
        assetId,
        vk.id(),
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        exclusion.id(),
        NOW.plusSeconds(2)
    );
    Optional<ChannelHistoryTransition> restored = await(
        history.recordEligibilityDecision(allow)
    );

    assertThat(excluded).get().extracting(ChannelHistoryTransition::outcome)
        .isEqualTo(HistoryTransitionOutcome.DEACTIVATE);
    assertThat(restored).get().extracting(ChannelHistoryTransition::outcome)
        .isEqualTo(HistoryTransitionOutcome.REACTIVATE);
    assertThat(await(history.findMembershipState(assetId, vk.id())))
        .isEqualTo(HistoryMembershipState.ACTIVE);
    assertThat(await(history.findMembershipState(assetId, telegram.id())))
        .isEqualTo(HistoryMembershipState.ACTIVE);
    assertThat(countRows("channel_eligibility_decisions")).isEqualTo(2);
  }

  @Test
  void replayedOldExclusionCannotOverrideNewerAllow() {
    UUID assetId = insertAsset("1".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:replay-exclude");
    await(history.recordPublicationEvidence(
        confirmed(assetId, channel.id(), "vk:post:replay-exclude")
    ));
    ChannelEligibilityDecision exclusion = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.MANUAL,
        null,
        NOW.plusSeconds(1)
    );
    await(history.recordEligibilityDecision(exclusion));
    ChannelEligibilityDecision allow = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        exclusion.id(),
        NOW.plusSeconds(2)
    );
    await(history.recordEligibilityDecision(allow));

    Optional<ChannelHistoryTransition> replay = await(
        history.recordEligibilityDecision(exclusion)
    );

    assertThat(replay).isEmpty();
    assertThat(await(history.findMembershipState(assetId, channel.id())))
        .isEqualTo(HistoryMembershipState.ACTIVE);
    assertThat(countRows("channel_eligibility_decisions")).isEqualTo(2);
  }

  @Test
  void replayedOldAllowCannotOverrideNewerExclusion() {
    UUID assetId = insertAsset("2".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:replay-allow");
    await(history.recordPublicationEvidence(
        confirmed(assetId, channel.id(), "vk:post:replay-allow")
    ));
    ChannelEligibilityDecision allow = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        null,
        NOW.plusSeconds(1)
    );
    await(history.recordEligibilityDecision(allow));
    ChannelEligibilityDecision exclusion = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.MANUAL,
        allow.id(),
        NOW.plusSeconds(2)
    );
    await(history.recordEligibilityDecision(exclusion));

    Optional<ChannelHistoryTransition> replay = await(
        history.recordEligibilityDecision(allow)
    );

    assertThat(replay).isEmpty();
    assertThat(await(history.findMembershipState(assetId, channel.id())))
        .isEqualTo(HistoryMembershipState.INACTIVE);
    assertThat(countRows("channel_eligibility_decisions")).isEqualTo(2);
  }

  @Test
  void exclusionBeforeExternalImportKeepsHistoryAbsentUntilReallowed() {
    UUID assetId = insertAsset("c".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:3");
    ChannelEligibilityDecision exclusion = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.MANUAL,
        null,
        NOW
    );
    assertThat(await(history.recordEligibilityDecision(exclusion))).isEmpty();

    PublicationEvidence imported = confirmed(
        PublicationEvidenceSource.EXTERNAL_IMPORT,
        assetId,
        channel.id(),
        "vk:post:3"
    );
    ChannelHistoryTransition skipped = await(history.recordPublicationEvidence(imported));
    ChannelEligibilityDecision allow = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        exclusion.id(),
        NOW.plusSeconds(1)
    );
    ChannelHistoryTransition promoted = await(
        history.recordEligibilityDecision(allow)
    ).orElseThrow();

    assertThat(skipped.outcome()).isEqualTo(HistoryTransitionOutcome.KEEP_INACTIVE);
    assertThat(promoted.outcome()).isEqualTo(HistoryTransitionOutcome.PROMOTE);
    assertThat(await(history.findMembershipState(assetId, channel.id())))
        .isEqualTo(HistoryMembershipState.ACTIVE);
  }

  @Test
  void concurrentRepeatConfirmationCreatesOneOccurrenceAndMembership() {
    UUID assetId = insertAsset("d".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:4");
    PublicationEvidence first = confirmed(assetId, channel.id(), "vk:post:4");
    PublicationOccurrence secondOccurrence = new PublicationOccurrence(
        UUID.randomUUID(),
        assetId,
        channel.id(),
        first.occurrence().externalPublicationId(),
        first.occurrence().publishedAt()
    );
    PublicationEvidence second = new PublicationEvidence(
        PublicationEvidenceStatus.CONFIRMED,
        PublicationEvidenceSource.RECONCILIATION,
        assetId,
        channel.id(),
        secondOccurrence,
        NOW.plusSeconds(1)
    );

    List<Attempt> attempts = concurrently(
        () -> history.recordPublicationEvidence(first),
        () -> history.recordPublicationEvidence(second)
    );

    assertThat(attempts).extracting(Attempt::failure).containsOnlyNulls();
    assertThat(attempts).extracting(attempt -> attempt.transition().outcome())
        .containsExactlyInAnyOrder(
            HistoryTransitionOutcome.PROMOTE,
            HistoryTransitionOutcome.KEEP_ACTIVE
        );
    assertThat(countRows("publication_occurrences")).isEqualTo(1);
    assertThat(countRows("channel_history_memberships")).isEqualTo(1);
  }

  @Test
  void migrationUpgradesV11WithoutChangingCatalogAsset() throws Exception {
    String upgradeSchema = "history_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(upgradeSchema)
        .defaultSchema(upgradeSchema)
        .target(MigrationVersion.fromVersion("11"))
        .load()
        .migrate();
    UUID assetId = UUID.randomUUID();
    String sha = "e".repeat(64);
    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO %s.catalog_media_assets
            (id, sha256, mime_type, width, height, storage_reference)
          VALUES
            ('%s', '%s', 'image/jpeg', 1280, 720, '%s')
          """.formatted(
          upgradeSchema,
          assetId,
          sha,
          StorageReference.fromSha256(sha).value()
      ));
    }

    Flyway upgraded = Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(upgradeSchema)
        .defaultSchema(upgradeSchema)
        .load();
    upgraded.migrate();
    upgraded.validate();

    try (Connection connection = jdbcConnection();
         Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
             SELECT sha256,
                    to_regclass('%s.channel_history_memberships') AS history_table
             FROM %s.catalog_media_assets
             WHERE id = '%s'
             """.formatted(upgradeSchema, upgradeSchema, assetId))) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("sha256")).isEqualTo(sha);
      assertThat(result.getString("history_table")).isNotNull();
    }
  }

  @Test
  void decisionMustSupersedeLatestAndAdvanceTime() {
    UUID assetId = insertAsset("f".repeat(64));
    ChannelProfile channel = registerChannel(PublicationPlatform.VK, "vk:group:5");
    ChannelEligibilityDecision first = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.MANUAL,
        null,
        NOW
    );
    await(history.recordEligibilityDecision(first));
    ChannelEligibilityDecision stale = decision(
        assetId,
        channel.id(),
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        null,
        NOW.plusSeconds(1)
    );

    assertThatThrownBy(() -> await(history.recordEligibilityDecision(stale)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("eligibility decision must supersede the latest decision");
    assertThat(countRows("channel_eligibility_decisions")).isEqualTo(1);
  }

  private ChannelProfile registerChannel(PublicationPlatform platform, String externalId) {
    return await(history.registerChannel(new ChannelProfile(
        UUID.randomUUID(),
        platform,
        externalId,
        "Fixture channel"
    )));
  }

  private UUID insertAsset(String sha256) {
    UUID id = UUID.randomUUID();
    await(databaseClient.sql("""
            INSERT INTO catalog_media_assets (
              id, sha256, mime_type, width, height, storage_reference
            ) VALUES (
              :id, :sha256, 'image/jpeg', 1280, 720, :storage_reference
            )
            """)
        .bind("id", id)
        .bind("sha256", sha256)
        .bind("storage_reference", StorageReference.fromSha256(sha256).value())
        .then()
        .toFuture());
    return id;
  }

  private long countRows(String table) {
    return await(databaseClient.sql("SELECT COUNT(*) AS count FROM " + table)
        .map((row, metadata) -> row.get("count", Long.class))
        .one()
        .toFuture());
  }

  private Flyway flyway(String targetSchema) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(targetSchema)
        .defaultSchema(targetSchema)
        .load();
  }

  private Connection jdbcConnection() throws Exception {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(),
        POSTGRES.getUsername(),
        POSTGRES.getPassword()
    );
  }

  private static PublicationEvidence confirmed(UUID assetId, UUID channelId, String externalId) {
    return confirmed(
        PublicationEvidenceSource.PUBLISH_ATTEMPT,
        assetId,
        channelId,
        externalId
    );
  }

  private static PublicationEvidence confirmed(
      PublicationEvidenceSource source,
      UUID assetId,
      UUID channelId,
      String externalId
  ) {
    PublicationOccurrence occurrence = new PublicationOccurrence(
        UUID.randomUUID(),
        assetId,
        channelId,
        externalId,
        NOW
    );
    return new PublicationEvidence(
        PublicationEvidenceStatus.CONFIRMED,
        source,
        assetId,
        channelId,
        occurrence,
        NOW
    );
  }

  private static PublicationEvidence unconfirmed(
      PublicationEvidenceStatus status,
      UUID assetId,
      UUID channelId
  ) {
    return new PublicationEvidence(
        status,
        PublicationEvidenceSource.PUBLISH_ATTEMPT,
        assetId,
        channelId,
        null,
        NOW
    );
  }

  private static ChannelEligibilityDecision decision(
      UUID assetId,
      UUID channelId,
      EligibilityDecisionType type,
      EligibilityReason reason,
      UUID supersedes,
      Instant decidedAt
  ) {
    return new ChannelEligibilityDecision(
        UUID.randomUUID(),
        assetId,
        channelId,
        type,
        reason,
        null,
        supersedes,
        decidedAt
    );
  }

  private List<Attempt> concurrently(
      Supplier<CompletionStage<ChannelHistoryTransition>> first,
      Supplier<CompletionStage<ChannelHistoryTransition>> second
  ) {
    CyclicBarrier barrier = new CyclicBarrier(2);
    try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Attempt> firstFuture = CompletableFuture.supplyAsync(
          () -> attemptAfterBarrier(barrier, first),
          workers
      );
      CompletableFuture<Attempt> secondFuture = CompletableFuture.supplyAsync(
          () -> attemptAfterBarrier(barrier, second),
          workers
      );
      return List.of(await(firstFuture), await(secondFuture));
    }
  }

  private Attempt attemptAfterBarrier(
      CyclicBarrier barrier,
      Supplier<CompletionStage<ChannelHistoryTransition>> operation
  ) {
    try {
      barrier.await();
      return new Attempt(await(operation.get()), null);
    } catch (Throwable failure) {
      return new Attempt(null, failure);
    }
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

  private record Attempt(ChannelHistoryTransition transition, Throwable failure) {
  }
}
