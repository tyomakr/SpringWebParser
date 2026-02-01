package ru.tyomakr.akcp.jobs.service;

import java.time.Instant;
import java.util.UUID;
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

  public JobService(JobRepository jobRepository) {
    this.jobRepository = jobRepository;
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
        null
    );
    return jobRepository.insert(row).map(JobMapper::toJob);
  }

  public Mono<Job> getJob(UUID id) {
    return jobRepository.findById(id).map(JobMapper::toJob);
  }

  public Flux<Job> fetchQueued(int limit) {
    return jobRepository.findQueued(limit).map(JobMapper::toJob);
  }

  public Mono<Void> markDone(UUID id) {
    return jobRepository.updateStatus(id, JobStatus.DONE.name(), null);
  }

  public Mono<Void> markInProgress(UUID id) {
    return jobRepository.updateStatus(id, JobStatus.IN_PROGRESS.name(), null);
  }

  public Mono<Void> markFailed(UUID id, String error) {
    return jobRepository.updateStatus(id, JobStatus.FAILED.name(), error);
  }

  public Mono<Void> updatePayload(UUID id, String payload) {
    return jobRepository.updatePayload(id, payload);
  }
}
