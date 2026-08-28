package ru.tyomakr.akcp.ingestion.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.JobType;
import ru.tyomakr.akcp.ingestion.web.dto.FishkiParseJobResponse;
import ru.tyomakr.akcp.ingestion.web.dto.FishkiParseJobStatus;
import ru.tyomakr.akcp.ingestion.web.dto.FishkiParseRequest;
import ru.tyomakr.akcp.ingestion.web.dto.FishkiParseResult;
import ru.tyomakr.akcp.ingestion.web.service.FishkiIngestionService;
import ru.tyomakr.akcp.ingestion.web.service.FishkiParseJobPayload;
import ru.tyomakr.akcp.jobs.service.JobService;

@RestController
@RequestMapping("/api/ingestion/web/fishki")
public class FishkiIngestionController {
  private final FishkiIngestionService fishkiIngestionService;
  private final JobService jobService;
  private final ObjectMapper objectMapper;

  public FishkiIngestionController(
      FishkiIngestionService fishkiIngestionService,
      JobService jobService,
      ObjectMapper objectMapper
  ) {
    this.fishkiIngestionService = fishkiIngestionService;
    this.jobService = jobService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/parse")
  public Mono<FishkiParseResult> parse(@RequestBody FishkiParseRequest request) {
    int pageFrom = request.resolvedPageFrom();
    int pageTo = request.resolvedPageTo();
    return fishkiIngestionService.parseRange(pageFrom, pageTo, request.resolvedCreateItem());
  }

  @PostMapping("/parse-async")
  public Mono<FishkiParseJobResponse> parseAsync(@RequestBody FishkiParseRequest request) {
    int pageFrom = request.resolvedPageFrom();
    int pageTo = request.resolvedPageTo();
    FishkiParseJobPayload payload = new FishkiParseJobPayload(pageFrom, pageTo, request.resolvedCreateItem(), null, null);
    return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
        .flatMap(json -> jobService.createJob(JobType.FISHKI_PARSE, json))
        .map(job -> new FishkiParseJobResponse(job.id(), job.status().name()));
  }

  @GetMapping("/jobs/{id}")
  public Mono<FishkiParseJobStatus> getJob(@PathVariable UUID id) {
    return jobService.getJob(id)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found")))
        .flatMap(job -> {
          if (job.type() != JobType.FISHKI_PARSE) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a Fishki job"));
          }
          FishkiParseJobPayload payload = readPayload(job.payload());
          return Mono.just(new FishkiParseJobStatus(
              job.id(),
              job.status().name(),
              payload == null ? null : payload.pageFrom(),
              payload == null ? null : payload.pageTo(),
              payload == null ? null : payload.createdItemId(),
              payload == null ? null : payload.attachmentsCount(),
              job.lastError()
          ));
        });
  }

  private FishkiParseJobPayload readPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(payload, FishkiParseJobPayload.class);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }
}
