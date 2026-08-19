package ru.tyomakr.akcp.core.content;

import java.util.Map;
import java.util.Objects;

public record AnalysisExplanation(String code, String message, Map<String, String> evidence) {
  public AnalysisExplanation {
    Objects.requireNonNull(code, "code is required");
    Objects.requireNonNull(message, "message is required");
    Objects.requireNonNull(evidence, "evidence is required");
    if (code.isBlank() || message.isBlank()) {
      throw new IllegalArgumentException("code and message must not be blank");
    }
    evidence = Map.copyOf(evidence);
  }
}
