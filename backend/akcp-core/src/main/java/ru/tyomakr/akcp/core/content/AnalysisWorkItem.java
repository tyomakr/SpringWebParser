package ru.tyomakr.akcp.core.content;

import java.util.Objects;
import java.util.UUID;

public record AnalysisWorkItem(MediaAnalysisInput input) {
  public AnalysisWorkItem {
    Objects.requireNonNull(input, "input is required");
  }

  public UUID assetId() {
    return input.assetId();
  }

  public String idempotencyKey() {
    return input.assetId() + ":" + input.inputSha256() + ":" + input.profile().value();
  }
}
