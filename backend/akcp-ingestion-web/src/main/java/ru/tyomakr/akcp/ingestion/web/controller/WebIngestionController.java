package ru.tyomakr.akcp.ingestion.web.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.ingestion.web.dto.WebParseRequest;
import ru.tyomakr.akcp.ingestion.web.dto.WebParseResponse;
import ru.tyomakr.akcp.ingestion.web.service.WebIngestionService;

@RestController
@RequestMapping("/api/ingestion/web")
public class WebIngestionController {
  private final WebIngestionService webIngestionService;

  public WebIngestionController(WebIngestionService webIngestionService) {
    this.webIngestionService = webIngestionService;
  }

  @PostMapping("/parse")
  public Mono<WebParseResponse> parse(@Valid @RequestBody WebParseRequest request) {
    return webIngestionService.parseAndMaybeCreate(request.url(), request.createItem())
        .map(result -> new WebParseResponse(
            result.url(),
            result.title(),
            result.attachments(),
            result.createdItemId()
        ));
  }
}
