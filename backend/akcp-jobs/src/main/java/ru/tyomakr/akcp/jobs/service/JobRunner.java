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
    jobService.fetchQueued(10)
        .flatMap(this::processJob)
        .subscribe();
  }

  private Mono<Void> processJob(Job job) {
    JobHandler handler = handlers.get(job.type());
    if (handler == null) {
      log.warn("No handler for job type {}", job.type());
      return jobService.markFailed(job.id(), "No handler for job type " + job.type());
    }
    return jobService.markInProgress(job.id())
        .then(handler.handle(job))
        .then(jobService.markDone(job.id()))
        .onErrorResume(ex -> {
          log.warn("Job {} failed", job.id(), ex);
          return jobService.markFailed(job.id(), ex.getMessage());
        });
  }
}
