package ru.tyomakr.akcp.library.media.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.DuplicateObservation;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ImportObservation;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ImportOutcome;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ModerationDecision;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.ModerationObservation;
import ru.tyomakr.akcp.library.media.metrics.OfflineMetricsInput.TextFilterObservation;

class OfflineMetricsCalculatorTest {
  private final OfflineMetricsCalculator calculator = new OfflineMetricsCalculator();

  @Test
  void calculatesGoldenFixtureReportDeterministically() {
    OfflineMetricsInput input = new OfflineMetricsInput(
        List.of(
            new ImportObservation("one", ImportOutcome.IMPORTED),
            new ImportObservation("two", ImportOutcome.IMPORTED),
            new ImportObservation("three", ImportOutcome.IMPORTED),
            new ImportObservation("one-replay", ImportOutcome.DUPLICATE),
            new ImportObservation("two-replay", ImportOutcome.DUPLICATE),
            new ImportObservation("broken", ImportOutcome.FAILED)
        ),
        List.of(
            new DuplicateObservation(true, true),
            new DuplicateObservation(true, true),
            new DuplicateObservation(true, false),
            new DuplicateObservation(false, true),
            new DuplicateObservation(false, false)
        ),
        List.of(
            new DuplicateObservation(true, true),
            new DuplicateObservation(true, true),
            new DuplicateObservation(true, true),
            new DuplicateObservation(true, false),
            new DuplicateObservation(false, true),
            new DuplicateObservation(false, false)
        ),
        List.of(
            new TextFilterObservation(true, false),
            new TextFilterObservation(true, false),
            new TextFilterObservation(true, false),
            new TextFilterObservation(true, true),
            new TextFilterObservation(false, true)
        ),
        List.of(
            new ModerationObservation(1, ModerationDecision.APPROVE),
            new ModerationObservation(2, ModerationDecision.REJECT),
            new ModerationObservation(3, ModerationDecision.SKIP),
            new ModerationObservation(4, ModerationDecision.UNDECIDED),
            new ModerationObservation(5, ModerationDecision.APPROVE)
        ),
        List.of(
            List.of(1.0d, 0.0d),
            List.of(1.0d, 0.0d),
            List.of(0.0d, 1.0d)
        ),
        3
    );

    OfflineMetricsReport report = calculator.calculate(input);

    assertThat(report.importCounts())
        .isEqualTo(new OfflineMetricsReport.ImportCounts(6, 3, 2, 1));
    assertThat(report.exactDeduplication())
        .isEqualTo(new OfflineMetricsReport.BinaryClassificationMetrics(2, 1, 1, 1, 0.666667d, 0.666667d));
    assertThat(report.nearDeduplication())
        .isEqualTo(new OfflineMetricsReport.BinaryClassificationMetrics(3, 1, 1, 1, 0.75d, 0.75d));
    assertThat(report.textExclusion())
        .isEqualTo(new OfflineMetricsReport.TextExclusionMetrics(4, 1, 0.25d));
    assertThat(report.moderation())
        .isEqualTo(new OfflineMetricsReport.ModerationMetrics(
            5, 4, 2, 1, 1, 0.8d, 0.666667d, 3, 0.333333d
        ));
    assertThat(report.intraListDiversity()).isEqualTo(0.666667d);
    assertThat(report.biasedPositiveSampleWarning())
        .isEqualTo(OfflineMetricsCalculator.BIASED_POSITIVE_SAMPLE_WARNING);
    assertThat(calculator.calculate(input)).isEqualTo(report);
  }

  @Test
  void usesZeroForMetricsWithoutAValidDenominator() {
    OfflineMetricsReport report = calculator.calculate(new OfflineMetricsInput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        10
    ));

    assertThat(report.exactDeduplication().precision()).isZero();
    assertThat(report.exactDeduplication().recall()).isZero();
    assertThat(report.textExclusion().falseExclusionRate()).isZero();
    assertThat(report.moderation().decisionCoverage()).isZero();
    assertThat(report.moderation().acceptanceAmongApproveReject()).isZero();
    assertThat(report.moderation().precisionAtKProxy()).isZero();
    assertThat(report.intraListDiversity()).isZero();
  }

  @Test
  void rejectsInvalidEmbeddingFixtures() {
    OfflineMetricsInput mismatchedDimensions = inputWithEmbeddings(List.of(
        List.of(1.0d, 0.0d),
        List.of(1.0d)
    ));
    OfflineMetricsInput zeroVector = inputWithEmbeddings(List.of(
        List.of(1.0d, 0.0d),
        List.of(0.0d, 0.0d)
    ));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(mismatchedDimensions))
        .withMessageContaining("same dimension");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> calculator.calculate(zeroVector))
        .withMessageContaining("norm");
  }

  @Test
  void rejectsPartialModerationSnapshots() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new OfflineMetricsInput(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new ModerationObservation(2, ModerationDecision.APPROVE)),
            List.of(),
            1
        ))
        .withMessageContaining("complete served snapshot");
  }

  private OfflineMetricsInput inputWithEmbeddings(List<List<Double>> embeddings) {
    return new OfflineMetricsInput(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        embeddings,
        1
    );
  }
}
