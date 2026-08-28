package ru.tyomakr.akcp.jobs.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import io.r2dbc.spi.Readable;
import ru.tyomakr.akcp.jobs.persistence.JobRow;

final class JobRowMappers {
  private JobRowMappers() {
  }

  static JobRow toJobRow(Readable row) {
    OffsetDateTime createdAt = row.get("created_at", OffsetDateTime.class);
    OffsetDateTime updatedAt = row.get("updated_at", OffsetDateTime.class);
    OffsetDateTime leaseUntil = row.get("lease_until", OffsetDateTime.class);
    Integer attemptCount = row.get("attempt_count", Integer.class);
    return new JobRow(
        row.get("id", UUID.class),
        row.get("type", String.class),
        row.get("status", String.class),
        row.get("payload", String.class),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null,
        row.get("last_error", String.class),
        attemptCount != null ? attemptCount : 0,
        leaseUntil != null ? leaseUntil.toInstant() : null,
        row.get("claim_token", UUID.class),
        row.get("external_result", String.class)
    );
  }
}
