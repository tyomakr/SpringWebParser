package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.Objects;

public record AnalysisWorkerReport(int requested, List<AnalysisItemOutcome> outcomes) {
  public AnalysisWorkerReport {
    Objects.requireNonNull(outcomes, "outcomes is required");
    if (requested <= 0 || outcomes.size() > requested) {
      throw new IllegalArgumentException("requested must be positive and cover outcomes");
    }
    outcomes = List.copyOf(outcomes);
  }

  public long created() {
    return outcomes.stream().filter(item -> item.status() == AnalysisItemOutcomeStatus.CREATED).count();
  }

  public long reused() {
    return outcomes.stream().filter(item -> item.status() == AnalysisItemOutcomeStatus.REUSED).count();
  }

  public long failed() {
    return outcomes.stream().filter(item -> item.status() == AnalysisItemOutcomeStatus.FAILED).count();
  }
}
