package ru.tyomakr.akcp.core.content;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A bounded, caller-supplied authorized export. It never implies network access. */
public record SourceImportBatch(
    UUID batchId,
    SourcePlatform platform,
    String sourceConnectionId,
    List<SourceImportRecord> records
) {
  public SourceImportBatch {
    Objects.requireNonNull(batchId, "batchId is required");
    Objects.requireNonNull(platform, "platform is required");
    Objects.requireNonNull(records, "records are required");
    if (records.isEmpty()) {
      throw new IllegalArgumentException("records must not be empty");
    }
    records = List.copyOf(records);
    sourceConnectionId = normalizeOptional(sourceConnectionId);
    long uniqueRecords = records.stream()
        .map(SourceImportRecord::sourceRecordId)
        .distinct()
        .count();
    if (uniqueRecords != records.size()) {
      throw new IllegalArgumentException("sourceRecordId must be unique within a batch");
    }
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
