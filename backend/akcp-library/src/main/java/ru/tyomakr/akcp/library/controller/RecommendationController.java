package ru.tyomakr.akcp.library.controller;

import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.dto.RecommendationBackfillRequest;
import ru.tyomakr.akcp.library.dto.RecommendationBackfillResponse;
import ru.tyomakr.akcp.library.dto.RecommendationFeatureResponse;
import ru.tyomakr.akcp.library.dto.RecommendationFeatureUpsertRequest;
import ru.tyomakr.akcp.library.dto.RecommendationFeedbackRequest;
import ru.tyomakr.akcp.library.dto.RecommendationFeedbackResponse;
import ru.tyomakr.akcp.library.dto.RecommendationTopResponse;
import ru.tyomakr.akcp.library.service.RecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
  private final RecommendationService service;

  public RecommendationController(RecommendationService service) {
    this.service = service;
  }

  @PostMapping("/features")
  public Mono<RecommendationFeatureResponse> upsertFeature(@RequestBody RecommendationFeatureUpsertRequest request) {
    return service.upsertFeature(request)
        .map(row -> new RecommendationFeatureResponse(
            row.id(),
            row.dataset(),
            row.attachmentId(),
            row.imageUrl(),
            row.textDominant()
        ));
  }

  @GetMapping("/top")
  public Mono<RecommendationTopResponse> top(
      @RequestParam UUID referenceAttachmentId,
      @RequestParam(required = false) Integer limit,
      Principal principal
  ) {
    String username = principal == null ? null : principal.getName();
    return service.topRecommendations(username, referenceAttachmentId, limit);
  }

  @PostMapping("/feedback")
  public Mono<RecommendationFeedbackResponse> saveFeedback(
      @RequestBody RecommendationFeedbackRequest request,
      Principal principal
  ) {
    String username = principal == null ? null : principal.getName();
    return service.saveFeedback(username, request)
        .map(row -> new RecommendationFeedbackResponse(
            row.id(),
            row.action(),
            row.createdAt(),
            row.servingEventId(),
            row.servedRank(),
            row.reason()
        ));
  }

  @PostMapping("/backfill")
  public Mono<RecommendationBackfillResponse> backfill(@RequestBody(required = false) RecommendationBackfillRequest request) {
    Integer limit = request == null ? null : request.limit();
    return service.backfillFeatures(limit);
  }
}
