package ru.tyomakr.akcp.library.media.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-memory observations used to calculate an offline recommendation report.
 * No observation in this contract is treated as authorization to publish.
 */
public record OfflineMetricsInput(
    List<ImportObservation> imports,
    List<DuplicateObservation> exactDuplicatePairs,
    List<DuplicateObservation> nearDuplicatePairs,
    List<TextFilterObservation> textFilterObservations,
    List<ModerationObservation> moderationObservations,
    List<List<Double>> recommendationEmbeddings,
    int precisionAtK
) {
  public OfflineMetricsInput {
    imports = List.copyOf(imports);
    exactDuplicatePairs = List.copyOf(exactDuplicatePairs);
    nearDuplicatePairs = List.copyOf(nearDuplicatePairs);
    textFilterObservations = List.copyOf(textFilterObservations);
    moderationObservations = List.copyOf(moderationObservations);
    recommendationEmbeddings = recommendationEmbeddings.stream()
        .map(List::copyOf)
        .toList();
    if (precisionAtK < 1) {
      throw new IllegalArgumentException("precisionAtK must be positive");
    }
    Set<Integer> ranks = new HashSet<>();
    for (ModerationObservation observation : moderationObservations) {
      if (!ranks.add(observation.rank())) {
        throw new IllegalArgumentException("moderation ranks must be unique");
      }
    }
    for (int expectedRank = 1; expectedRank <= moderationObservations.size(); expectedRank++) {
      if (!ranks.contains(expectedRank)) {
        throw new IllegalArgumentException(
            "moderation observations must represent the complete served snapshot with continuous ranks"
        );
      }
    }
  }

  public enum ImportOutcome {
    IMPORTED,
    DUPLICATE,
    FAILED
  }

  public enum ModerationDecision {
    APPROVE,
    REJECT,
    SKIP,
    UNDECIDED
  }

  public record ImportObservation(String sourceId, ImportOutcome outcome) {
    public ImportObservation {
      if (sourceId == null || sourceId.isBlank()) {
        throw new IllegalArgumentException("sourceId is required");
      }
      if (outcome == null) {
        throw new IllegalArgumentException("outcome is required");
      }
    }
  }

  public record DuplicateObservation(boolean expectedDuplicate, boolean predictedDuplicate) {
  }

  public record TextFilterObservation(boolean acceptableForModeration, boolean predictedExcluded) {
  }

  public record ModerationObservation(int rank, ModerationDecision decision) {
    public ModerationObservation {
      if (rank < 1) {
        throw new IllegalArgumentException("rank must be positive");
      }
      if (decision == null) {
        throw new IllegalArgumentException("decision is required");
      }
    }
  }
}
