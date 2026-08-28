package ru.tyomakr.akcp.library.media.metrics;

public record OfflineMetricsReport(
    ImportCounts importCounts,
    BinaryClassificationMetrics exactDeduplication,
    BinaryClassificationMetrics nearDeduplication,
    TextExclusionMetrics textExclusion,
    ModerationMetrics moderation,
    double intraListDiversity,
    String biasedPositiveSampleWarning
) {
  public record ImportCounts(int total, int imported, int duplicates, int failed) {
  }

  public record BinaryClassificationMetrics(
      int truePositive,
      int falsePositive,
      int falseNegative,
      int trueNegative,
      double precision,
      double recall
  ) {
  }

  public record TextExclusionMetrics(
      int acceptableImages,
      int falselyExcludedImages,
      double falseExclusionRate
  ) {
  }

  public record ModerationMetrics(
      int served,
      int decided,
      int approved,
      int rejected,
      int skipped,
      double decisionCoverage,
      double acceptanceAmongApproveReject,
      int precisionAtK,
      double precisionAtKProxy
  ) {
  }
}
