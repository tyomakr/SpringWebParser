package ru.tyomakr.akcp.jobs.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;

@Component
public class JobRunner {
  private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

  private final JobService jobService;
  private final Map<JobType, JobHandler> handlers;

  public JobRunner(JobService jobService, List<JobHandler> handlers) {
    this.jobService = jobService;
    this.handlers = handlers.stream()
        .collect(Collectors.toMap(JobHandler::type, Function.identity(), (a, b) -> a));
  }

  @Scheduled(fixedDelayString = "${akcp.jobs.poll-interval-ms:10000}")
  public void runTick() {
    jobService.fetchClaimable(10)
        .flatMap(job -> jobService.claimJob(job.id()))
        .flatMap(this::processJob)
        .subscribe();
  }

  private Mono<Void> processJob(JobClaim claim) {
    Job job = claim.job();
    JobHandler handler = handlers.get(job.type());
    if (handler == null) {
      log.warn("No handler for job type {}", job.type());
      return jobService.markFailed(claim, "No handler for job type " + job.type())
          .then();
    }
    return handler.handle(job)
        .onErrorResume(ex -> {
          log.warn("Job {} failed", job.id(), ex);
          return jobService.markFailed(claim, ex.getMessage()).then(Mono.empty());
        })
        .flatMap(result -> persistResult(claim, result))
        .then();
  }

  private Mono<Boolean> persistResult(JobClaim claim, JobExecutionResult result) {
    Mono<Boolean> update = switch (result.status()) {
      case DONE -> jobService.markDone(claim, result.externalResult());
      case UNKNOWN -> jobService.markUnknown(
          claim,
          result.externalResult(),
          result.detail()
      );
      default -> Mono.error(new IllegalArgumentException(
          "Unsupported handler result status " + result.status()
      ));
    };
    return update.doOnNext(updated -> {
      if (!updated) {
        log.warn("Job {} outcome {} ignored because its claim is no longer current",
            claim.job().id(), result.status());
      }
    });
  }
}
