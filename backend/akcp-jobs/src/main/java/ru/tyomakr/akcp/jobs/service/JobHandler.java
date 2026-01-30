package ru.tyomakr.akcp.jobs.service;

import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;

public interface JobHandler {
  JobType type();

  Mono<Void> handle(Job job);
}
