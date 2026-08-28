package ru.tyomakr.akcp.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelHistoryTransitionServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
  private final ChannelHistoryTransitionService service = new ChannelHistoryTransitionService();

  @ParameterizedTest
  @MethodSource("confirmedSources")
  void confirmedPublicationPromotesOnlyForItsChannel(PublicationEvidenceSource source) {
    PublicationOccurrence occurrence = occurrence(UUID.randomUUID(), UUID.randomUUID());

    ChannelHistoryTransition transition = service.evaluate(
        confirmed(source, occurrence),
        null,
        HistoryMembershipState.ABSENT
    );

    assertEquals(HistoryTransitionOutcome.PROMOTE, transition.outcome());
    assertEquals(HistoryMembershipState.ACTIVE, transition.resultingState());
    assertTrue(transition.explanation().contains("channel history"));
  }

  @ParameterizedTest
  @EnumSource(value = PublicationEvidenceStatus.class, names = {"FAILED", "UNKNOWN"})
  void failedAndUnknownAttemptsNeverPromote(PublicationEvidenceStatus status) {
    PublicationEvidence evidence = new PublicationEvidence(
        status,
        PublicationEvidenceSource.PUBLISH_ATTEMPT,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        NOW
    );

    ChannelHistoryTransition absent = service.evaluate(
        evidence,
        null,
        HistoryMembershipState.ABSENT
    );
    ChannelHistoryTransition active = service.evaluate(
        evidence,
        null,
        HistoryMembershipState.ACTIVE
    );

    assertEquals(HistoryTransitionOutcome.IGNORE_UNCONFIRMED, absent.outcome());
    assertEquals(HistoryMembershipState.ABSENT, absent.resultingState());
    assertEquals(HistoryMembershipState.ACTIVE, active.resultingState());
  }

  @Test
  void repeatConfirmationIsIdempotent() {
    PublicationOccurrence occurrence = occurrence(UUID.randomUUID(), UUID.randomUUID());

    ChannelHistoryTransition transition = service.evaluate(
        confirmed(PublicationEvidenceSource.PUBLISH_ATTEMPT, occurrence),
        null,
        HistoryMembershipState.ACTIVE
    );

    assertEquals(HistoryTransitionOutcome.KEEP_ACTIVE, transition.outcome());
    assertEquals(HistoryMembershipState.ACTIVE, transition.resultingState());
  }

  @ParameterizedTest
  @EnumSource(value = EligibilityReason.class, names = {"TEXT_DOMINANT", "MANUAL"})
  void reasonedChannelExclusionPreventsOrDeactivatesMembership(EligibilityReason reason) {
    UUID assetId = UUID.randomUUID();
    UUID channelId = UUID.randomUUID();
    PublicationOccurrence occurrence = occurrence(assetId, channelId);
    ChannelEligibilityDecision exclusion = eligibility(
        assetId,
        channelId,
        EligibilityDecisionType.EXCLUDE,
        reason,
        null
    );

    ChannelHistoryTransition absent = service.evaluate(
        confirmed(PublicationEvidenceSource.PUBLISH_ATTEMPT, occurrence),
        exclusion,
        HistoryMembershipState.ABSENT
    );
    ChannelHistoryTransition active = service.evaluate(
        confirmed(PublicationEvidenceSource.RECONCILIATION, occurrence),
        exclusion,
        HistoryMembershipState.ACTIVE
    );

    assertEquals(HistoryTransitionOutcome.KEEP_INACTIVE, absent.outcome());
    assertEquals(HistoryMembershipState.ABSENT, absent.resultingState());
    assertEquals(HistoryTransitionOutcome.DEACTIVATE, active.outcome());
    assertEquals(HistoryMembershipState.INACTIVE, active.resultingState());
    assertTrue(active.explanation().contains(reason.name()));
  }

  @Test
  void laterAllowDecisionReactivatesMembership() {
    UUID assetId = UUID.randomUUID();
    UUID channelId = UUID.randomUUID();
    UUID exclusionId = UUID.randomUUID();
    ChannelEligibilityDecision allow = new ChannelEligibilityDecision(
        UUID.randomUUID(),
        assetId,
        channelId,
        EligibilityDecisionType.ALLOW,
        EligibilityReason.MANUAL,
        "Moderator restored eligibility",
        exclusionId,
        NOW.plusSeconds(1)
    );

    ChannelHistoryTransition transition = service.evaluate(
        confirmed(
            PublicationEvidenceSource.EXTERNAL_IMPORT,
            occurrence(assetId, channelId)
        ),
        allow,
        HistoryMembershipState.INACTIVE
    );

    assertEquals(HistoryTransitionOutcome.REACTIVATE, transition.outcome());
    assertEquals(HistoryMembershipState.ACTIVE, transition.resultingState());
  }

  @Test
  void eligibilityForAnotherChannelCannotAffectPublication() {
    UUID assetId = UUID.randomUUID();
    PublicationOccurrence occurrence = occurrence(assetId, UUID.randomUUID());
    ChannelEligibilityDecision otherChannel = eligibility(
        assetId,
        UUID.randomUUID(),
        EligibilityDecisionType.EXCLUDE,
        EligibilityReason.MANUAL,
        null
    );

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> service.evaluate(
        confirmed(PublicationEvidenceSource.PUBLISH_ATTEMPT, occurrence),
        otherChannel,
        HistoryMembershipState.ABSENT
    ));
    assertEquals(
        "eligibility decision must match publication asset and channel",
        exception.getMessage()
    );
  }

  @Test
  void unconfirmedReconciliationIsRejectedByContract() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationEvidence(
            PublicationEvidenceStatus.UNKNOWN,
            PublicationEvidenceSource.RECONCILIATION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            NOW
        )
    );
    assertEquals("reconciliation and external import must be confirmed", exception.getMessage());
  }

  private static Stream<PublicationEvidenceSource> confirmedSources() {
    return Stream.of(PublicationEvidenceSource.values());
  }

  private static PublicationEvidence confirmed(
      PublicationEvidenceSource source,
      PublicationOccurrence occurrence
  ) {
    return new PublicationEvidence(
        PublicationEvidenceStatus.CONFIRMED,
        source,
        occurrence.mediaAssetId(),
        occurrence.channelProfileId(),
        occurrence,
        NOW
    );
  }

  private static PublicationOccurrence occurrence(UUID assetId, UUID channelId) {
    return new PublicationOccurrence(
        UUID.randomUUID(),
        assetId,
        channelId,
        "publication:" + UUID.randomUUID(),
        NOW
    );
  }

  private static ChannelEligibilityDecision eligibility(
      UUID assetId,
      UUID channelId,
      EligibilityDecisionType decision,
      EligibilityReason reason,
      UUID supersedes
  ) {
    return new ChannelEligibilityDecision(
        UUID.randomUUID(),
        assetId,
        channelId,
        decision,
        reason,
        null,
        supersedes,
        NOW
    );
  }
}
