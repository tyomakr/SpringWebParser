package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record ChannelHistoryTransition(
    HistoryTransitionOutcome outcome,
    HistoryMembershipState resultingState,
    String explanation
) {
  public ChannelHistoryTransition {
    Objects.requireNonNull(outcome, "outcome is required");
    Objects.requireNonNull(resultingState, "resultingState is required");
    Objects.requireNonNull(explanation, "explanation is required");
    if (explanation.isBlank()) {
      throw new IllegalArgumentException("explanation must not be blank");
    }
  }
}
