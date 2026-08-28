package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only, channel-scoped eligibility decision. A later decision may supersede an earlier one. */
public record ChannelEligibilityDecision(
    UUID id,
    UUID mediaAssetId,
    UUID channelProfileId,
    EligibilityDecisionType decision,
    EligibilityReason reason,
    String reasonDetail,
    UUID supersedesDecisionId,
    Instant decidedAt
) {
  public ChannelEligibilityDecision {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(channelProfileId, "channelProfileId is required");
    Objects.requireNonNull(decision, "decision is required");
    Objects.requireNonNull(reason, "reason is required");
    Objects.requireNonNull(decidedAt, "decidedAt is required");
    reasonDetail = normalizeOptional(reasonDetail);
    if (decision == EligibilityDecisionType.EXCLUDE
        && reason == EligibilityReason.DEFAULT_POLICY) {
      throw new IllegalArgumentException("exclusion requires a specific reason");
    }
    if (reason == EligibilityReason.OTHER && reasonDetail == null) {
      throw new IllegalArgumentException("OTHER reason requires reasonDetail");
    }
    if (id.equals(supersedesDecisionId)) {
      throw new IllegalArgumentException("decision cannot supersede itself");
    }
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
