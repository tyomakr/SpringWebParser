package ru.tyomakr.akcp.ingestion.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.Job;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.jobs.service.JobHandler;
import ru.tyomakr.akcp.jobs.service.JobService;

@Component
public class FishkiParseJobHandler implements JobHandler {
  private final FishkiIngestionService fishkiIngestionService;
  private final JobService jobService;
  private final ObjectMapper objectMapper;

  public FishkiParseJobHandler(
      FishkiIngestionService fishkiIngestionService,
      JobService jobService,
      ObjectMapper objectMapper
  ) {
    this.fishkiIngestionService = fishkiIngestionService;
    this.jobService = jobService;
    this.objectMapper = objectMapper;
  }

  @Override
  public JobType type() {
    return JobType.FISHKI_PARSE;
  }

  @Override
  public Mono<Void> handle(Job job) {
    return Mono.fromCallable(() -> objectMapper.readValue(job.payload(), FishkiParseJobPayload.class))
        .flatMap(payload -> fishkiIngestionService
            .parseRange(payload.pageFrom(), payload.pageTo(), payload.createItem())
            .flatMap(result -> {
              FishkiParseJobPayload updated = payload.withResult(result.createdItemId(), result.attachments().size());
              return Mono.fromCallable(() -> objectMapper.writeValueAsString(updated));
            })
            .flatMap(updatedJson -> jobService.updatePayload(job.id(), updatedJson)))
        .then();
  }
}
