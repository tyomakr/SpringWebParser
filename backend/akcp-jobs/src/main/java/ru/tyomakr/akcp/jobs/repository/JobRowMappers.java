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
    return new JobRow(
        row.get("id", UUID.class),
        row.get("type", String.class),
        row.get("status", String.class),
        row.get("payload", String.class),
        createdAt != null ? createdAt.toInstant() : null,
        updatedAt != null ? updatedAt.toInstant() : null,
        row.get("last_error", String.class)
    );
  }
}
