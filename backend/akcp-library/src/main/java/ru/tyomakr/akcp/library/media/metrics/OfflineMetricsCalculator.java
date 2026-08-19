package ru.tyomakr.akcp.library.media.metrics;

import java.util.List;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.DuplicateObservation;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ImportOutcome;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ModerationDecision;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ModerationObservation;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsReport.BinaryClassificationMetrics;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsReport.ImportCounts;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsReport.ModerationMetrics;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsReport.TextExclusionMetrics;

/**
 * Deterministic, side-effect-free metrics for fixture and human-review sessions.
 */
public final class OfflineMetricsCalculator {
  public static final String BIASED_POSITIVE_SAMPLE_WARNING =
      "Published history is a biased positive sample, not ground-truth quality labels.";

  public OfflineMetricsReport calculate(OfflineMetricsInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input is required");
    }
    return new OfflineMetricsReport(
        importCounts(input),
        classification(input.exactDuplicatePairs()),
        classification(input.nearDuplicatePairs()),
        textExclusion(input),
        moderation(input),
        intraListDiversity(input.recommendationEmbeddings()),
        BIASED_POSITIVE_SAMPLE_WARNING
    );
  }

  private ImportCounts importCounts(OfflineMetricsInput input) {
    int imported = 0;
    int duplicates = 0;
    int failed = 0;
    for (OfflineMetricsInput.ImportObservation observation : input.imports()) {
      if (observation.outcome() == ImportOutcome.IMPORTED) {
        imported++;
      } else if (observation.outcome() == ImportOutcome.DUPLICATE) {
        duplicates++;
      } else {
        failed++;
      }
    }
    return new ImportCounts(input.imports().size(), imported, duplicates, failed);
  }

  private BinaryClassificationMetrics classification(List<DuplicateObservation> observations) {
    int truePositive = 0;
    int falsePositive = 0;
    int falseNegative = 0;
    int trueNegative = 0;
    for (DuplicateObservation observation : observations) {
      if (observation.expectedDuplicate() && observation.predictedDuplicate()) {
        truePositive++;
      } else if (!observation.expectedDuplicate() && observation.predictedDuplicate()) {
        falsePositive++;
      } else if (observation.expectedDuplicate()) {
        falseNegative++;
      } else {
        trueNegative++;
      }
    }
    return new BinaryClassificationMetrics(
        truePositive,
        falsePositive,
        falseNegative,
        trueNegative,
        ratio(truePositive, truePositive + falsePositive),
        ratio(truePositive, truePositive + falseNegative)
    );
  }

  private TextExclusionMetrics textExclusion(OfflineMetricsInput input) {
    int acceptable = 0;
    int falselyExcluded = 0;
    for (OfflineMetricsInput.TextFilterObservation observation : input.textFilterObservations()) {
      if (observation.acceptableForModeration()) {
        acceptable++;
        if (observation.predictedExcluded()) {
          falselyExcluded++;
        }
      }
    }
    return new TextExclusionMetrics(acceptable, falselyExcluded, ratio(falselyExcluded, acceptable));
  }

  private ModerationMetrics moderation(OfflineMetricsInput input) {
    int approved = 0;
    int rejected = 0;
    int skipped = 0;
    int approvedInTopK = 0;
    int servedInTopK = 0;
    for (ModerationObservation observation : input.moderationObservations()) {
      if (observation.rank() <= input.precisionAtK()) {
        servedInTopK++;
        if (observation.decision() == ModerationDecision.APPROVE) {
          approvedInTopK++;
        }
      }
      if (observation.decision() == ModerationDecision.APPROVE) {
        approved++;
      } else if (observation.decision() == ModerationDecision.REJECT) {
        rejected++;
      } else if (observation.decision() == ModerationDecision.SKIP) {
        skipped++;
      }
    }
    int served = input.moderationObservations().size();
    int decided = approved + rejected + skipped;
    return new ModerationMetrics(
        served,
        decided,
        approved,
        rejected,
        skipped,
        ratio(decided, served),
        ratio(approved, approved + rejected),
        input.precisionAtK(),
        ratio(approvedInTopK, servedInTopK)
    );
  }

  private double intraListDiversity(List<List<Double>> embeddings) {
    if (embeddings.size() < 2) {
      return 0.0d;
    }
    int dimension = embeddings.get(0).size();
    if (dimension == 0) {
      throw new IllegalArgumentException("embeddings must not be empty");
    }
    for (List<Double> embedding : embeddings) {
      validateEmbedding(embedding, dimension);
    }
    double distanceSum = 0.0d;
    int pairCount = 0;
    for (int left = 0; left < embeddings.size(); left++) {
      for (int right = left + 1; right < embeddings.size(); right++) {
        distanceSum += 1.0d - cosine(embeddings.get(left), embeddings.get(right));
        pairCount++;
      }
    }
    return round6(distanceSum / pairCount);
  }

  private void validateEmbedding(List<Double> embedding, int dimension) {
    if (embedding.size() != dimension) {
      throw new IllegalArgumentException("all embeddings must have the same dimension");
    }
    double norm = 0.0d;
    for (Double value : embedding) {
      if (value == null || !Double.isFinite(value)) {
        throw new IllegalArgumentException("embedding values must be finite");
      }
      norm += value * value;
    }
    if (norm == 0.0d) {
      throw new IllegalArgumentException("embedding norm must be positive");
    }
  }

  private double cosine(List<Double> left, List<Double> right) {
    double dot = 0.0d;
    double leftNorm = 0.0d;
    double rightNorm = 0.0d;
    for (int index = 0; index < left.size(); index++) {
      double leftValue = left.get(index);
      double rightValue = right.get(index);
      dot += leftValue * rightValue;
      leftNorm += leftValue * leftValue;
      rightNorm += rightValue * rightValue;
    }
    double cosine = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    return Math.max(-1.0d, Math.min(1.0d, cosine));
  }

  private double ratio(int numerator, int denominator) {
    return denominator == 0 ? 0.0d : round6((double) numerator / (double) denominator);
  }

  private double round6(double value) {
    return Math.round(value * 1_000_000d) / 1_000_000d;
  }
}
