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

  public Flux<JobRow> findClaimable(int limit) {
    return databaseClient.sql("""
            SELECT * FROM jobs
            WHERE status = 'QUEUED'
               OR (
                 status = 'IN_PROGRESS'
                 AND type <> 'PUBLISH_VK'
                 AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
               )
            ORDER BY created_at ASC
            LIMIT :limit
            """)
        .bind("limit", limit)
        .map(JobRowMappers::toJobRow)
        .all();
  }

  public Mono<JobRow> claim(UUID id, UUID token, long leaseMillis) {
    return databaseClient.sql("""
            UPDATE jobs
            SET status = 'IN_PROGRESS',
                attempt_count = attempt_count + 1,
                lease_until = CURRENT_TIMESTAMP + (:leaseMillis * INTERVAL '1 millisecond'),
                claim_token = :token,
                last_error = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND (
                status = 'QUEUED'
                OR (
                  status = 'IN_PROGRESS'
                  AND type <> 'PUBLISH_VK'
                  AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                )
              )
            RETURNING *
            """)
        .bind("id", id)
        .bind("token", token)
        .bind("leaseMillis", leaseMillis)
        .map(JobRowMappers::toJobRow)
        .one();
  }

  public Mono<Boolean> completeClaim(UUID id, UUID token, String externalResult) {
    return updateClaimStatus(id, token, "DONE", null, externalResult);
  }

  public Mono<Boolean> failClaim(UUID id, UUID token, String lastError) {
    return updateClaimStatus(id, token, "FAILED", lastError, null);
  }

  public Mono<Boolean> unknownClaim(UUID id, UUID token, String externalResult, String detail) {
    return updateClaimStatus(id, token, "UNKNOWN", detail, externalResult);
  }

  private Mono<Boolean> updateClaimStatus(
      UUID id,
      UUID token,
      String status,
      String lastError,
      String externalResult
  ) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            UPDATE jobs
            SET status = :status,
                last_error = :lastError,
                external_result = :externalResult,
                lease_until = NULL,
                claim_token = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND status = 'IN_PROGRESS'
              AND claim_token = :token
            """)
        .bind("status", status)
        .bind("id", id)
        .bind("token", token);
    spec = bindNullable(spec, "lastError", lastError, String.class);
    spec = bindNullable(spec, "externalResult", externalResult, String.class);
    return spec.fetch().rowsUpdated().map(updated -> updated == 1);
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
