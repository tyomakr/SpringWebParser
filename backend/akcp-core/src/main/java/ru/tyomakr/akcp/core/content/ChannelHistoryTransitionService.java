package ru.tyomakr.akcp.core.content;

import java.util.Objects;

/**
 * Pure policy for channel history. Confirmed publication is evidence for one channel, never a
 * universal quality label.
 */
public final class ChannelHistoryTransitionService {
  public ChannelHistoryTransition evaluate(
      PublicationEvidence evidence,
      ChannelEligibilityDecision latestEligibility,
      HistoryMembershipState currentState
  ) {
    Objects.requireNonNull(evidence, "evidence is required");
    Objects.requireNonNull(currentState, "currentState is required");
    if (evidence.status() != PublicationEvidenceStatus.CONFIRMED) {
      return transition(
          HistoryTransitionOutcome.IGNORE_UNCONFIRMED,
          currentState,
          "Publication outcome is not confirmed"
      );
    }

    PublicationOccurrence occurrence = evidence.occurrence();
    if (latestEligibility != null) {
      requireSameScope(occurrence, latestEligibility);
      if (latestEligibility.decision() == EligibilityDecisionType.EXCLUDE) {
        if (currentState == HistoryMembershipState.ACTIVE) {
          return transition(
              HistoryTransitionOutcome.DEACTIVATE,
              HistoryMembershipState.INACTIVE,
              "Excluded for channel: " + latestEligibility.reason()
          );
        }
        return transition(
            HistoryTransitionOutcome.KEEP_INACTIVE,
            currentState,
            "Excluded for channel: " + latestEligibility.reason()
        );
      }
    }

    return switch (currentState) {
      case ABSENT -> transition(
          HistoryTransitionOutcome.PROMOTE,
          HistoryMembershipState.ACTIVE,
          "Confirmed publication promotes asset to channel history"
      );
      case INACTIVE -> transition(
          HistoryTransitionOutcome.REACTIVATE,
          HistoryMembershipState.ACTIVE,
          "Later eligibility decision reactivates channel history membership"
      );
      case ACTIVE -> transition(
          HistoryTransitionOutcome.KEEP_ACTIVE,
          HistoryMembershipState.ACTIVE,
          "Channel history membership is already active"
      );
    };
  }

  private static void requireSameScope(
      PublicationOccurrence occurrence,
      ChannelEligibilityDecision eligibility
  ) {
    if (!occurrence.mediaAssetId().equals(eligibility.mediaAssetId())
        || !occurrence.channelProfileId().equals(eligibility.channelProfileId())) {
      throw new IllegalArgumentException(
          "eligibility decision must match publication asset and channel"
      );
    }
  }

  private static ChannelHistoryTransition transition(
      HistoryTransitionOutcome outcome,
      HistoryMembershipState resultingState,
      String explanation
  ) {
    return new ChannelHistoryTransition(outcome, resultingState, explanation);
  }
}
