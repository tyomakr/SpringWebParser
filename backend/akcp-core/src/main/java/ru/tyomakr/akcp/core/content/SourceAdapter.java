package ru.tyomakr.akcp.core.content;

import java.util.concurrent.CompletionStage;

/** Source boundary. Implementations must use authorized exports or explicitly approved APIs. */
public interface SourceAdapter {
  SourcePlatform platform();

  /** Normalizes an authorized batch; catalog matching belongs to ingestion. */
  CompletionStage<SourceImportBatch> normalize(SourceImportBatch batch);
}
