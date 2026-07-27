package ru.tyomakr.akcp.core.content;

import java.util.Objects;

public record AnalysisProfileVersion(String value) {
  public AnalysisProfileVersion {
    Objects.requireNonNull(value, "value is required");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
