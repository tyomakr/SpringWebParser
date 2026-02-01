package ru.tyomakr.akcp.jobs.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.jobs.persistence.JobRow;

@Repository
public class JobRepository {
  private final DatabaseClient databaseClient;

  public JobRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Mono<JobRow> insert(JobRow row) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO jobs (id, type, status, payload, created_at, updated_at, last_error)
            VALUES (:id, :type, :status, :payload, :createdAt, :updatedAt, :lastError)
            RETURNING *
            """)
        .bind("id", row.id())
        .bind("type", row.type())
        .bind("status", row.status())
        .bind("createdAt", row.createdAt())
        .bind("updatedAt", row.updatedAt());
    spec = bindNullable(spec, "payload", row.payload(), String.class);
    spec = bindNullable(spec, "lastError", row.lastError(), String.class);
    return spec.map(JobRowMappers::toJobRow).one();
  }

  public Mono<JobRow> findById(UUID id) {
    return databaseClient.sql("SELECT * FROM jobs WHERE id = :id")
        .bind("id", id)
        .map(JobRowMappers::toJobRow)
        .one();
  }

  public Flux<JobRow> findQueued(int limit) {
    return databaseClient.sql("""
            SELECT * FROM jobs
            WHERE status = 'QUEUED'
            ORDER BY created_at ASC
            LIMIT :limit
            """)
        .bind("limit", limit)
        .map(JobRowMappers::toJobRow)
        .all();
  }

  public Mono<Void> updateStatus(UUID id, String status, String lastError) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            UPDATE jobs
            SET status = :status, last_error = :lastError, updated_at = :updatedAt
            WHERE id = :id
            """)
        .bind("status", status)
        .bind("updatedAt", Instant.now())
        .bind("id", id);
    spec = bindNullable(spec, "lastError", lastError, String.class);
    return spec.then();
  }

  public Mono<Void> updatePayload(UUID id, String payload) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            UPDATE jobs
            SET payload = :payload, updated_at = :updatedAt
            WHERE id = :id
            """)
        .bind("payload", payload)
        .bind("updatedAt", Instant.now())
        .bind("id", id);
    return spec.then();
  }

  private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
    if (value == null) {
      return spec.bindNull(name, type);
    }
    return spec.bind(name, value);
  }
}
