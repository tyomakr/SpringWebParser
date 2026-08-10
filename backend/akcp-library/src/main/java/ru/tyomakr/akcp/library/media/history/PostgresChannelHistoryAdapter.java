package ru.tyomakr.akcp.library.media.history;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.content.ChannelEligibilityDecision;
import ru.tyomakr.akcp.core.content.ChannelHistoryPort;
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

/** PostgreSQL persistence seam for channel-scoped publication history; not runtime-wired. */
public final class PostgresChannelHistoryAdapter implements ChannelHistoryPort {
  private final DatabaseClient databaseClient;
  private final TransactionalOperator transactions;
  private final ChannelHistoryTransitionService policy;

  public PostgresChannelHistoryAdapter(
      DatabaseClient databaseClient,
      TransactionalOperator transactions,
      ChannelHistoryTransitionService policy
  ) {
    this.databaseClient = Objects.requireNonNull(databaseClient, "databaseClient is required");
    this.transactions = Objects.requireNonNull(transactions, "transactions is required");
    this.policy = Objects.requireNonNull(policy, "policy is required");
  }

  @Override
  public CompletionStage<ChannelProfile> registerChannel(ChannelProfile channel) {
    Objects.requireNonNull(channel, "channel is required");
    return transactions.transactional(
        lock("channel:" + channel.platform() + ":" + channel.externalChannelId())
            .then(findChannelById(channel.id()))
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(existingById -> {
              validateChannelId(existingById, channel);
              return insertChannel(channel);
            })
            .then(findChannelByNaturalKey(channel))
            .switchIfEmpty(Mono.error(
                new IllegalStateException("channel invariant violated: channel is missing")
            ))
            .flatMap(stored -> {
              validateCompatibleChannel(stored, channel);
              return Mono.just(stored);
            })
    ).toFuture();
  }

  @Override
  public CompletionStage<ChannelHistoryTransition> recordPublicationEvidence(
      PublicationEvidence evidence
  ) {
    Objects.requireNonNull(evidence, "evidence is required");
    return transactions.transactional(
        lockScope(evidence.mediaAssetId(), evidence.channelProfileId())
            .then(findMembershipStateInternal(
                evidence.mediaAssetId(),
                evidence.channelProfileId()
            ))
            .flatMap(currentState -> {
              if (evidence.status() != PublicationEvidenceStatus.CONFIRMED) {
                return Mono.just(policy.evaluate(evidence, null, currentState));
              }
              return registerOccurrence(evidence)
                  .flatMap(occurrence -> findLatestEligibility(
                      evidence.mediaAssetId(),
                      evidence.channelProfileId()
                  ).map(Optional::of).defaultIfEmpty(Optional.empty())
                      .flatMap(latest -> applyTransition(
                          occurrence,
                          latest.orElse(null),
                          currentState,
                          policy.evaluate(evidence, latest.orElse(null), currentState),
                          evidence.observedAt()
                      )));
            })
    ).toFuture();
  }

  @Override
  public CompletionStage<Optional<ChannelHistoryTransition>> recordEligibilityDecision(
      ChannelEligibilityDecision decision
  ) {
    Objects.requireNonNull(decision, "decision is required");
    return transactions.transactional(
        lockScope(decision.mediaAssetId(), decision.channelProfileId())
            .then(findEligibilityById(decision.id()))
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(existingById -> {
              if (existingById.isPresent()) {
                validateSameDecision(existingById.orElseThrow(), decision);
                return Mono.just(Optional.<ChannelEligibilityDecision>empty());
              }
              return findLatestEligibility(
                  decision.mediaAssetId(),
                  decision.channelProfileId()
              ).map(Optional::of).defaultIfEmpty(Optional.empty())
                  .flatMap(latest -> {
                    validateDecisionChain(latest, decision);
                    return insertEligibility(decision).thenReturn(Optional.of(decision));
                  });
            })
            .flatMap(storedDecision -> {
              if (storedDecision.isEmpty()) {
                return Mono.just(Optional.<ChannelHistoryTransition>empty());
              }
              ChannelEligibilityDecision newlyStoredDecision = storedDecision.orElseThrow();
              return findLatestOccurrence(
                decision.mediaAssetId(),
                decision.channelProfileId()
            ).map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(latestOccurrence -> {
                  if (latestOccurrence.isEmpty()) {
                    return Mono.just(Optional.<ChannelHistoryTransition>empty());
                  }
                  return findMembershipStateInternal(
                      decision.mediaAssetId(),
                      decision.channelProfileId()
                  ).flatMap(currentState -> {
                    PublicationOccurrence occurrence = latestOccurrence.orElseThrow();
                    PublicationEvidence reevaluation = new PublicationEvidence(
                        PublicationEvidenceStatus.CONFIRMED,
                        PublicationEvidenceSource.RECONCILIATION,
                        decision.mediaAssetId(),
                        decision.channelProfileId(),
                        occurrence,
                        decision.decidedAt()
                    );
                    ChannelHistoryTransition transition = policy.evaluate(
                        reevaluation,
                        newlyStoredDecision,
                        currentState
                    );
                    return applyTransition(
                        occurrence,
                        newlyStoredDecision,
                        currentState,
                        transition,
                        decision.decidedAt()
                    ).map(Optional::of);
                  });
                });
            })
    ).toFuture();
  }

  @Override
  public CompletionStage<HistoryMembershipState> findMembershipState(
      UUID mediaAssetId,
      UUID channelProfileId
  ) {
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(channelProfileId, "channelProfileId is required");
    return findMembershipStateInternal(mediaAssetId, channelProfileId).toFuture();
  }

  private Mono<ChannelHistoryTransition> applyTransition(
      PublicationOccurrence occurrence,
      ChannelEligibilityDecision eligibility,
      HistoryMembershipState currentState,
      ChannelHistoryTransition transition,
      java.time.Instant changedAt
  ) {
    return switch (transition.outcome()) {
      case PROMOTE -> insertMembership(occurrence, eligibility, changedAt)
          .thenReturn(transition);
      case REACTIVATE -> updateMembership(
          occurrence,
          eligibility,
          true,
          changedAt
      ).thenReturn(transition);
      case DEACTIVATE -> updateMembership(
          occurrence,
          eligibility,
          false,
          changedAt
      ).thenReturn(transition);
      case KEEP_INACTIVE -> currentState == HistoryMembershipState.INACTIVE
          ? updateMembership(occurrence, eligibility, false, changedAt).thenReturn(transition)
          : Mono.just(transition);
      case KEEP_ACTIVE, IGNORE_UNCONFIRMED -> Mono.just(transition);
    };
  }

  private Mono<PublicationOccurrence> registerOccurrence(PublicationEvidence evidence) {
    PublicationOccurrence candidate = evidence.occurrence();
    return findOccurrenceById(candidate.id())
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(existingById -> {
          validateOccurrenceId(existingById, candidate);
          return insertOccurrence(candidate, evidence.source(), evidence.observedAt());
        })
        .then(findOccurrenceByNaturalKey(candidate))
        .switchIfEmpty(Mono.error(
            new IllegalStateException("publication invariant violated: occurrence is missing")
        ))
        .flatMap(stored -> {
          validateCompatibleOccurrence(stored, candidate);
          return Mono.just(stored);
        });
  }

  private Mono<Void> lockScope(UUID mediaAssetId, UUID channelProfileId) {
    return lock("history:" + mediaAssetId + ":" + channelProfileId);
  }

  private Mono<Void> lock(String key) {
    return databaseClient.sql("SELECT pg_advisory_xact_lock(hashtext(:lock_key))")
        .bind("lock_key", key)
        .map((row, metadata) -> Boolean.TRUE)
        .one()
        .then();
  }

  private Mono<Void> insertChannel(ChannelProfile channel) {
    return databaseClient.sql("""
            INSERT INTO channel_profiles (id, platform, external_channel_id, display_name)
            VALUES (:id, :platform, :external_channel_id, :display_name)
            ON CONFLICT DO NOTHING
            """)
        .bind("id", channel.id())
        .bind("platform", channel.platform().name())
        .bind("external_channel_id", channel.externalChannelId())
        .bind("display_name", channel.displayName())
        .then();
  }

  private Mono<Void> insertOccurrence(
      PublicationOccurrence occurrence,
      PublicationEvidenceSource source,
      java.time.Instant confirmedAt
  ) {
    return databaseClient.sql("""
            INSERT INTO publication_occurrences (
              id, media_asset_id, channel_profile_id, external_publication_id,
              published_at, confirmation_source, confirmed_at
            ) VALUES (
              :id, :media_asset_id, :channel_profile_id, :external_publication_id,
              :published_at, :confirmation_source, :confirmed_at
            )
            ON CONFLICT DO NOTHING
            """)
        .bind("id", occurrence.id())
        .bind("media_asset_id", occurrence.mediaAssetId())
        .bind("channel_profile_id", occurrence.channelProfileId())
        .bind("external_publication_id", occurrence.externalPublicationId())
        .bind("published_at", utc(occurrence.publishedAt()))
        .bind("confirmation_source", source.name())
        .bind("confirmed_at", utc(confirmedAt))
        .then();
  }

  private Mono<Void> insertEligibility(ChannelEligibilityDecision decision) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO channel_eligibility_decisions (
              id, media_asset_id, channel_profile_id, decision, reason,
              reason_detail, supersedes_decision_id, decided_at
            ) VALUES (
              :id, :media_asset_id, :channel_profile_id, :decision, :reason,
              :reason_detail, :supersedes_decision_id, :decided_at
            )
            """)
        .bind("id", decision.id())
        .bind("media_asset_id", decision.mediaAssetId())
        .bind("channel_profile_id", decision.channelProfileId())
        .bind("decision", decision.decision().name())
        .bind("reason", decision.reason().name())
        .bind("decided_at", utc(decision.decidedAt()));
    spec = bindNullable(spec, "reason_detail", decision.reasonDetail(), String.class);
    spec = bindNullable(
        spec,
        "supersedes_decision_id",
        decision.supersedesDecisionId(),
        UUID.class
    );
    return spec.then();
  }

  private Mono<Void> insertMembership(
      PublicationOccurrence occurrence,
      ChannelEligibilityDecision eligibility,
      java.time.Instant changedAt
  ) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO channel_history_memberships (
              id, media_asset_id, channel_profile_id, publication_occurrence_id,
              eligibility_decision_id, active, created_at, updated_at
            ) VALUES (
              :id, :media_asset_id, :channel_profile_id, :publication_occurrence_id,
              :eligibility_decision_id, TRUE, :changed_at, :changed_at
            )
            """)
        .bind("id", UUID.randomUUID())
        .bind("media_asset_id", occurrence.mediaAssetId())
        .bind("channel_profile_id", occurrence.channelProfileId())
        .bind("publication_occurrence_id", occurrence.id())
        .bind("changed_at", utc(changedAt));
    spec = bindNullable(
        spec,
        "eligibility_decision_id",
        eligibility == null ? null : eligibility.id(),
        UUID.class
    );
    return spec.then();
  }

  private Mono<Void> updateMembership(
      PublicationOccurrence occurrence,
      ChannelEligibilityDecision eligibility,
      boolean active,
      java.time.Instant changedAt
  ) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            UPDATE channel_history_memberships
            SET publication_occurrence_id = :publication_occurrence_id,
                eligibility_decision_id = :eligibility_decision_id,
                active = :active,
                updated_at = :changed_at
            WHERE media_asset_id = :media_asset_id
              AND channel_profile_id = :channel_profile_id
            """)
        .bind("publication_occurrence_id", occurrence.id())
        .bind("active", active)
        .bind("changed_at", utc(changedAt))
        .bind("media_asset_id", occurrence.mediaAssetId())
        .bind("channel_profile_id", occurrence.channelProfileId());
    spec = bindNullable(
        spec,
        "eligibility_decision_id",
        eligibility == null ? null : eligibility.id(),
        UUID.class
    );
    return spec.then();
  }

  private Mono<HistoryMembershipState> findMembershipStateInternal(
      UUID mediaAssetId,
      UUID channelProfileId
  ) {
    return databaseClient.sql("""
            SELECT active
            FROM channel_history_memberships
            WHERE media_asset_id = :media_asset_id
              AND channel_profile_id = :channel_profile_id
            """)
        .bind("media_asset_id", mediaAssetId)
        .bind("channel_profile_id", channelProfileId)
        .map((row, metadata) -> Boolean.TRUE.equals(row.get("active", Boolean.class))
            ? HistoryMembershipState.ACTIVE
            : HistoryMembershipState.INACTIVE)
        .one()
        .defaultIfEmpty(HistoryMembershipState.ABSENT);
  }

  private Mono<ChannelProfile> findChannelById(UUID id) {
    return databaseClient.sql("""
            SELECT id, platform, external_channel_id, display_name
            FROM channel_profiles WHERE id = :id
            """)
        .bind("id", id)
        .map((row, metadata) -> toChannel(row))
        .one();
  }

  private Mono<ChannelProfile> findChannelByNaturalKey(ChannelProfile channel) {
    return databaseClient.sql("""
            SELECT id, platform, external_channel_id, display_name
            FROM channel_profiles
            WHERE platform = :platform AND external_channel_id = :external_channel_id
            """)
        .bind("platform", channel.platform().name())
        .bind("external_channel_id", channel.externalChannelId())
        .map((row, metadata) -> toChannel(row))
        .one();
  }

  private Mono<PublicationOccurrence> findOccurrenceById(UUID id) {
    return occurrenceQuery("WHERE id = :id").bind("id", id)
        .map((row, metadata) -> toOccurrence(row)).one();
  }

  private Mono<PublicationOccurrence> findOccurrenceByNaturalKey(
      PublicationOccurrence occurrence
  ) {
    return occurrenceQuery("""
            WHERE channel_profile_id = :channel_profile_id
              AND external_publication_id = :external_publication_id
            """)
        .bind("channel_profile_id", occurrence.channelProfileId())
        .bind("external_publication_id", occurrence.externalPublicationId())
        .map((row, metadata) -> toOccurrence(row)).one();
  }

  private Mono<PublicationOccurrence> findLatestOccurrence(UUID assetId, UUID channelId) {
    return occurrenceQuery("""
            WHERE media_asset_id = :media_asset_id
              AND channel_profile_id = :channel_profile_id
            ORDER BY published_at DESC, id DESC
            LIMIT 1
            """)
        .bind("media_asset_id", assetId)
        .bind("channel_profile_id", channelId)
        .map((row, metadata) -> toOccurrence(row)).one();
  }

  private DatabaseClient.GenericExecuteSpec occurrenceQuery(String suffix) {
    return databaseClient.sql("""
        SELECT id, media_asset_id, channel_profile_id, external_publication_id, published_at
        FROM publication_occurrences
        """ + suffix);
  }

  private Mono<ChannelEligibilityDecision> findEligibilityById(UUID id) {
    return eligibilityQuery("WHERE id = :id").bind("id", id)
        .map((row, metadata) -> toEligibility(row)).one();
  }

  private Mono<ChannelEligibilityDecision> findLatestEligibility(UUID assetId, UUID channelId) {
    return eligibilityQuery("""
            WHERE media_asset_id = :media_asset_id
              AND channel_profile_id = :channel_profile_id
            ORDER BY decided_at DESC, id DESC
            LIMIT 1
            """)
        .bind("media_asset_id", assetId)
        .bind("channel_profile_id", channelId)
        .map((row, metadata) -> toEligibility(row)).one();
  }

  private DatabaseClient.GenericExecuteSpec eligibilityQuery(String suffix) {
    return databaseClient.sql("""
        SELECT id, media_asset_id, channel_profile_id, decision, reason,
               reason_detail, supersedes_decision_id, decided_at
        FROM channel_eligibility_decisions
        """ + suffix);
  }

  private static ChannelProfile toChannel(io.r2dbc.spi.Readable row) {
    return new ChannelProfile(
        row.get("id", UUID.class),
        PublicationPlatform.valueOf(row.get("platform", String.class)),
        row.get("external_channel_id", String.class),
        row.get("display_name", String.class)
    );
  }

  private static PublicationOccurrence toOccurrence(io.r2dbc.spi.Readable row) {
    OffsetDateTime publishedAt = row.get("published_at", OffsetDateTime.class);
    return new PublicationOccurrence(
        row.get("id", UUID.class),
        row.get("media_asset_id", UUID.class),
        row.get("channel_profile_id", UUID.class),
        row.get("external_publication_id", String.class),
        publishedAt.toInstant()
    );
  }

  private static ChannelEligibilityDecision toEligibility(io.r2dbc.spi.Readable row) {
    OffsetDateTime decidedAt = row.get("decided_at", OffsetDateTime.class);
    return new ChannelEligibilityDecision(
        row.get("id", UUID.class),
        row.get("media_asset_id", UUID.class),
        row.get("channel_profile_id", UUID.class),
        EligibilityDecisionType.valueOf(row.get("decision", String.class)),
        EligibilityReason.valueOf(row.get("reason", String.class)),
        row.get("reason_detail", String.class),
        row.get("supersedes_decision_id", UUID.class),
        decidedAt.toInstant()
    );
  }

  private static void validateChannelId(Optional<ChannelProfile> existing, ChannelProfile value) {
    if (existing.isPresent() && !existing.orElseThrow().equals(value)) {
      throw new IllegalStateException("channel ID is already registered differently");
    }
  }

  private static void validateCompatibleChannel(ChannelProfile stored, ChannelProfile candidate) {
    if (!stored.platform().equals(candidate.platform())
        || !stored.externalChannelId().equals(candidate.externalChannelId())
        || !stored.displayName().equals(candidate.displayName())) {
      throw new IllegalStateException("channel identity conflicts with existing channel");
    }
  }

  private static void validateOccurrenceId(
      Optional<PublicationOccurrence> existing,
      PublicationOccurrence value
  ) {
    if (existing.isPresent() && !existing.orElseThrow().equals(value)) {
      throw new IllegalStateException("publication occurrence ID is already registered differently");
    }
  }

  private static void validateCompatibleOccurrence(
      PublicationOccurrence stored,
      PublicationOccurrence candidate
  ) {
    if (!stored.mediaAssetId().equals(candidate.mediaAssetId())
        || !stored.channelProfileId().equals(candidate.channelProfileId())
        || !stored.externalPublicationId().equals(candidate.externalPublicationId())
        || !stored.publishedAt().equals(candidate.publishedAt())) {
      throw new IllegalStateException("external publication points to different content");
    }
  }

  private static void validateSameDecision(
      ChannelEligibilityDecision stored,
      ChannelEligibilityDecision candidate
  ) {
    if (!stored.equals(candidate)) {
      throw new IllegalStateException("eligibility decision ID is already registered differently");
    }
  }

  private static void validateDecisionChain(
      Optional<ChannelEligibilityDecision> latest,
      ChannelEligibilityDecision candidate
  ) {
    if (latest.isEmpty()) {
      if (candidate.supersedesDecisionId() != null) {
        throw new IllegalStateException("first eligibility decision cannot supersede a decision");
      }
      return;
    }
    ChannelEligibilityDecision previous = latest.orElseThrow();
    if (!previous.id().equals(candidate.supersedesDecisionId())) {
      throw new IllegalStateException("eligibility decision must supersede the latest decision");
    }
    if (!candidate.decidedAt().isAfter(previous.decidedAt())) {
      throw new IllegalStateException("eligibility decision time must advance");
    }
  }

  private static OffsetDateTime utc(java.time.Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
      DatabaseClient.GenericExecuteSpec spec,
      String name,
      T value,
      Class<T> type
  ) {
    return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
  }
}
