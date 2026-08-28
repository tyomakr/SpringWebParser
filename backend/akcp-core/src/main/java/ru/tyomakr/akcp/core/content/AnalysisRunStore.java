package ru.tyomakr.akcp.core.content;

import java.util.concurrent.CompletionStage;

public interface AnalysisRunStore {
  /** Idempotency key is asset id + input SHA + profile; partial results must not be stored. */
  CompletionStage<AnalysisRunWriteResult> saveIfAbsent(MediaAnalysisResult result);
}
