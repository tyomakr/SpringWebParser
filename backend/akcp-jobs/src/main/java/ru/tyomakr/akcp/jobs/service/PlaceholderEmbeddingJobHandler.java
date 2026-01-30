package ru.tyomakr.akcp.jobs.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;

@Component
public class PlaceholderEmbeddingJobHandler implements JobHandler {
  @Override
  public JobType type() {
    return JobType.COMPUTE_EMBEDDING;
  }

  @Override
  public Mono<Void> handle(Job job) {
    return Mono.empty();
  }
}
