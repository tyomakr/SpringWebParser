package ru.tyomakr.akcp.jobs.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobStatus;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.jobs.persistence.JobRow;
import ru.tyomakr.akcp.jobs.repository.JobRepository;

@Service
public class JobService {
  private final JobRepository jobRepository;
  private final MeterRegistry meterRegistry;
  private final Duration leaseDuration;

  public JobService(
      JobRepository jobRepository,
      MeterRegistry meterRegistry,
      @Value("${akcp.jobs.lease-duration:PT30M}") Duration leaseDuration
  ) {
    this.jobRepository = jobRepository;
    this.meterRegistry = meterRegistry;
    if (leaseDuration == null || leaseDuration.compareTo(Duration.ofMillis(1)) < 0) {
      throw new IllegalArgumentException("akcp.jobs.lease-duration must be at least 1ms");
    }
    this.leaseDuration = leaseDuration;
  }

  public Mono<Job> createJob(JobType type, String payload) {
    Instant now = Instant.now();
    JobRow row = new JobRow(
        UUID.randomUUID(),
        type.name(),
        JobStatus.QUEUED.name(),
        payload,
        now,
        now,
        null,
        0,
        null,
        null,
        null
    );
    return jobRepository.insert(row)
        .map(JobMapper::toJob)
        .doOnNext(job -> meterRegistry.counter(
            "akcp.jobs.created.total",
            "type",
            type.name()
        ).increment());
  }

  public Mono<Job> getJob(UUID id) {
    return jobRepository.findById(id).map(JobMapper::toJob);
  }

  public Flux<Job> fetchClaimable(int limit) {
    return jobRepository.findClaimable(limit).map(JobMapper::toJob);
  }

  public Mono<JobClaim> claimJob(UUID id) {
    UUID token = UUID.randomUUID();
    return jobRepository.claim(id, token, leaseDuration.toMillis())
        .map(row -> new JobClaim(JobMapper.toJob(row), token, row.leaseUntil()))
        .doOnNext(ignored -> incrementStatusMetric(JobStatus.IN_PROGRESS));
  }

  public Mono<Boolean> markDone(JobClaim claim, String externalResult) {
    return jobRepository.completeClaim(claim.job().id(), claim.token(), externalResult)
        .doOnNext(updated -> {
          if (updated) {
            incrementStatusMetric(JobStatus.DONE);
          }
        });
  }

  public Mono<Boolean> markFailed(JobClaim claim, String error) {
    return jobRepository.failClaim(claim.job().id(), claim.token(), error)
        .doOnNext(updated -> {
          if (updated) {
            incrementStatusMetric(JobStatus.FAILED);
          }
        });
  }

  public Mono<Boolean> markUnknown(JobClaim claim, String externalResult, String detail) {
    return jobRepository.unknownClaim(claim.job().id(), claim.token(), externalResult, detail)
        .doOnNext(updated -> {
          if (updated) {
            incrementStatusMetric(JobStatus.UNKNOWN);
          }
        });
  }

  public Mono<Void> updatePayload(UUID id, String payload) {
    return jobRepository.updatePayload(id, payload);
  }

  private void incrementStatusMetric(JobStatus status) {
    meterRegistry.counter(
        "akcp.jobs.status.updated.total",
        "status",
        status.name()
    ).increment();
  }
}
