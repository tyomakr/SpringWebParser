package ru.tyomakr.akcp.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.config.RecommendationProperties;
import ru.tyomakr.akcp.library.dto.RecommendationBackfillResponse;
import ru.tyomakr.akcp.library.dto.RecommendationCandidateResponse;
import ru.tyomakr.akcp.library.dto.RecommendationExplanationResponse;
import ru.tyomakr.akcp.library.dto.RecommendationExclusionResponse;
import ru.tyomakr.akcp.library.dto.RecommendationFeatureUpsertRequest;
import ru.tyomakr.akcp.library.dto.RecommendationFeedbackRequest;
import ru.tyomakr.akcp.library.dto.RecommendationTopResponse;
import ru.tyomakr.akcp.library.persistence.RecommendationAttachmentSourceRow;
import ru.tyomakr.akcp.library.persistence.RecommendationFeedbackRow;
import ru.tyomakr.akcp.library.persistence.RecommendationFeatureRow;
import ru.tyomakr.akcp.library.persistence.RecommendationItemProfileRow;
import ru.tyomakr.akcp.library.persistence.RecommendationServingEventRow;
import ru.tyomakr.akcp.library.repository.RecommendationRepository;

@Service
public class RecommendationService {
  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 50;
  private static final int CANDIDATE_POOL_LIMIT = 1200;
  private static final int HISTORY_POOL_LIMIT = 3000;
  private static final int PHASH_NEAR_DUP_THRESHOLD = 3;
  private static final int MAX_BACKFILL_LIMIT = 4000;
  private static final String RANKING_VERSION = "task6-ranking-v1";
  private static final String LEGACY_URL_ANALYSIS = "legacy-url-v1";
  private static final Map<RecommendationFeedbackAction, Set<String>> FEEDBACK_REASONS = Map.of(
      RecommendationFeedbackAction.APPROVE, Set.of("RELEVANT", "GOOD_FIT", "OTHER"),
      RecommendationFeedbackAction.REJECT, Set.of(
          "OFF_TOPIC", "TEXT_DOMINANT", "DUPLICATE", "LOW_QUALITY", "OTHER"
      ),
      RecommendationFeedbackAction.SKIP, Set.of("NOT_SURE", "NO_TIME", "TECHNICAL", "OTHER")
  );

  private final RecommendationRepository repository;
  private final RecommendationFeatureExtractor featureExtractor;
  private final RecommendationProperties properties;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;

  public RecommendationService(
      RecommendationRepository repository,
      RecommendationFeatureExtractor featureExtractor,
      RecommendationProperties properties,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry
  ) {
    this.repository = repository;
    this.featureExtractor = featureExtractor;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  public Mono<RecommendationFeatureRow> upsertFeature(RecommendationFeatureUpsertRequest request) {
    if (request == null) {
      return Mono.error(new IllegalArgumentException("request is required"));
    }
    String imageUrl = normalize(request.imageUrl());
    if (imageUrl == null) {
      return Mono.error(new IllegalArgumentException("imageUrl is required"));
    }

    RecommendationDataset dataset;
    try {
      dataset = RecommendationDataset.parse(request.dataset(), RecommendationDataset.CANDIDATE);
    } catch (IllegalArgumentException ex) {
      return Mono.error(new IllegalArgumentException("Unsupported dataset: " + request.dataset()));
    }
    try {
      validateManualFeature(request);
    } catch (IllegalArgumentException ex) {
      return Mono.error(ex);
    }

    String embeddingJson;
    try {
      embeddingJson = request.embedding() == null ? null : objectMapper.writeValueAsString(request.embedding());
    } catch (JsonProcessingException ex) {
      return Mono.error(new IllegalArgumentException("embedding is not serializable", ex));
    }

    Instant now = Instant.now();
    RecommendationFeatureRow row = new RecommendationFeatureRow(
        UUID.randomUUID(),
        dataset.name(),
        request.attachmentId(),
        null,
        imageUrl,
        normalizeSha256(request.sha256()),
        request.phash(),
        embeddingJson,
        request.textRatio(),
        Boolean.TRUE.equals(request.textDominant()),
        now,
        now,
        "manual-feature-v1",
        "{\"source\":\"authenticated-feature-api\"}"
    );
    if (row.attachmentId() != null) {
      return repository.findByAttachmentId(row.attachmentId())
          .flatMap(existing -> rejectCanonicalOverwrite(existing)
              .then(repository.upsertByAttachment(row)))
          .switchIfEmpty(repository.upsertByAttachment(row));
    }
    return repository.findByDatasetAndImageUrl(row.dataset(), row.imageUrl())
        .flatMap(existing -> rejectCanonicalOverwrite(existing)
            .then(repository.upsertByDatasetAndImageUrl(row)))
        .switchIfEmpty(repository.upsertByDatasetAndImageUrl(row));
  }

  public Mono<RecommendationBackfillResponse> backfillFeatures(Integer limitRaw) {
    long startedNanos = System.nanoTime();
    int limit = normalizeBackfillLimit(limitRaw);
    return repository.listAttachmentsWithoutFeatures(limit)
        .collectList()
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Mono.just(new RecommendationBackfillResponse(0, 0, toMillis(startedNanos)));
          }
          return reactor.core.publisher.Flux.fromIterable(rows)
              .concatMap(this::extractAndUpsert)
              .reduce(0, Integer::sum)
              .map(upserted -> new RecommendationBackfillResponse(rows.size(), upserted, toMillis(startedNanos)));
        })
        .doOnSuccess(response -> {
          meterRegistry.counter("akcp.recommendations.backfill.total", "result", "success").increment();
          meterRegistry.summary("akcp.recommendations.backfill.scanned").record(response.scanned());
          meterRegistry.summary("akcp.recommendations.backfill.upserted").record(response.upserted());
          meterRegistry.timer("akcp.recommendations.backfill.latency").record(response.durationMs(), TimeUnit.MILLISECONDS);
        })
        .doOnError(error -> meterRegistry.counter(
            "akcp.recommendations.backfill.total",
            "result",
            "error",
            "errorType",
            error.getClass().getSimpleName()
        ).increment());
  }

  public Mono<RecommendationTopResponse> topRecommendations(String username, UUID referenceAttachmentId, Integer limitRaw) {
    long startedNanos = System.nanoTime();
    if (username == null || username.isBlank()) {
      return Mono.error(new IllegalArgumentException("User is not authenticated"));
    }
    if (referenceAttachmentId == null) {
      return Mono.error(new IllegalArgumentException("referenceAttachmentId is required"));
    }
    int limit = normalizeLimit(limitRaw);
    String experimentGroup = properties.getExperimentGroup();
    UUID runId = UUID.randomUUID();

    Mono<RecommendationFeatureRow> referenceMono = repository.findByAttachmentId(referenceAttachmentId)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Reference feature is not found for attachment")));

    Mono<List<RecommendationFeatureRow>> candidatesMono = repository
        .listCandidates(RecommendationDataset.CANDIDATE.name(), referenceAttachmentId, CANDIDATE_POOL_LIMIT)
        .collectList();

    Mono<List<RecommendationFeatureRow>> historyMono = repository
        .listDatasetNonTextDominant(RecommendationDataset.VK_WALL.name(), HISTORY_POOL_LIMIT)
        .collectList();

    Mono<RecommendationItemProfileRow> referenceProfileMono = repository.findItemProfileByAttachmentId(referenceAttachmentId)
        .onErrorResume(ex -> {
          log.debug("Reference profile unavailable for {}: {}", referenceAttachmentId, ex.getMessage());
          return Mono.empty();
        })
        .defaultIfEmpty(new RecommendationItemProfileRow(referenceAttachmentId, null, null, null, List.of()));

    return Mono.zip(referenceMono, candidatesMono, historyMono, referenceProfileMono)
        .flatMap(tuple -> {
          RecommendationFeatureRow reference = tuple.getT1();
          List<RecommendationFeatureRow> candidates = tuple.getT2();
          List<RecommendationFeatureRow> history = tuple.getT3();
          RecommendationItemProfileRow referenceProfile = tuple.getT4();
          List<UUID> candidateIds = candidates.stream()
              .map(RecommendationFeatureRow::attachmentId)
              .filter(id -> id != null)
              .distinct()
              .toList();
          return repository.listItemProfilesByAttachmentIds(candidateIds)
              .collectMap(RecommendationItemProfileRow::attachmentId, value -> value)
              .map(candidateProfiles -> buildTop(
                  reference,
                  candidates,
                  history,
                  referenceProfile,
                  candidateProfiles,
                  referenceAttachmentId,
                  limit,
                  experimentGroup,
                  runId
              ));
        })
        .flatMap(response -> storeServingEvent(username, referenceAttachmentId, experimentGroup, response, startedNanos)
            .thenReturn(response))
        .doOnSuccess(response -> {
          meterRegistry.counter(
              "akcp.recommendations.top.total",
              "result",
              "success",
              "experiment",
              experimentGroup
          ).increment();
          meterRegistry.summary(
              "akcp.recommendations.top.returned",
              "experiment",
              experimentGroup
          ).record(response.returnedCount());
        })
        .doOnError(error -> meterRegistry.counter(
            "akcp.recommendations.top.total",
            "result",
            "error",
            "experiment",
            experimentGroup,
            "errorType",
            error.getClass().getSimpleName()
        ).increment())
        .doFinally(signalType -> meterRegistry.timer(
            "akcp.recommendations.top.latency",
            "experiment",
            experimentGroup
        ).record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS));
  }

  public Mono<RecommendationFeedbackRow> saveFeedback(String username, RecommendationFeedbackRequest request) {
    if (username == null || username.isBlank()) {
      return Mono.error(new IllegalArgumentException("User is not authenticated"));
    }
    if (request == null) {
      return Mono.error(new IllegalArgumentException("request is required"));
    }
    RecommendationFeedbackAction action;
    try {
      action = RecommendationFeedbackAction.parse(request.action());
    } catch (IllegalArgumentException ex) {
      return Mono.error(new IllegalArgumentException("Unsupported action: " + request.action()));
    }
    UUID runId = request.runId();
    if (runId == null) {
      return Mono.error(new IllegalArgumentException("runId is required"));
    }
    String reason = normalizeReason(action, request.reason());
    String note = normalize(request.note());
    if (note != null && note.length() > 1000) {
      return Mono.error(new IllegalArgumentException("note must not exceed 1000 characters"));
    }

    return repository.findServingEvent(runId, username)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Recommendation run is not found")))
        .flatMap(event -> validateServedCandidate(event, request))
        .flatMap(candidate -> repository.insertFeedback(new RecommendationFeedbackRow(
            UUID.randomUUID(),
            username,
            request.referenceAttachmentId(),
            request.recommendedAttachmentId(),
            action.name(),
            reason,
            runId,
            candidate.rank(),
            note,
            Instant.now()
        )))
        .doOnSuccess(saved -> meterRegistry.counter(
            "akcp.recommendations.feedback.total",
            "action",
            saved.action()
        ).increment());
  }

  private Mono<RecommendationCandidateResponse> validateServedCandidate(
      RecommendationServingEventRow event,
      RecommendationFeedbackRequest request
  ) {
    if (!event.referenceAttachmentId().equals(request.referenceAttachmentId())) {
      return Mono.error(new IllegalArgumentException(
          "referenceAttachmentId does not belong to recommendation run"
      ));
    }
    if (request.recommendedAttachmentId() == null) {
      return Mono.error(new IllegalArgumentException("recommendedAttachmentId is required"));
    }
    try {
      JsonNode snapshot = objectMapper.readTree(event.candidatesJson());
      JsonNode candidatesNode = snapshot.isArray() ? snapshot : snapshot.get("candidates");
      if (candidatesNode == null || !candidatesNode.isArray()) {
        throw new IllegalStateException("Recommendation snapshot has no candidates array");
      }
      List<RecommendationCandidateResponse> candidates = objectMapper.convertValue(
          candidatesNode,
          new TypeReference<List<RecommendationCandidateResponse>>() {
          }
      );
      RecommendationCandidateResponse matched = candidates.stream()
          .filter(candidate -> request.recommendedAttachmentId().equals(candidate.attachmentId()))
          .findFirst()
          .orElse(null);
      if (matched == null) {
        return Mono.error(new IllegalArgumentException(
            "recommendedAttachmentId does not belong to recommendation run"
        ));
      }
      if (request.servedRank() != null && request.servedRank() != matched.rank()) {
        return Mono.error(new IllegalArgumentException("servedRank does not match recommendation run"));
      }
      return Mono.just(matched);
    } catch (Exception ex) {
      return Mono.error(new IllegalStateException("Recommendation snapshot is unreadable", ex));
    }
  }

  private String normalizeReason(RecommendationFeedbackAction action, String raw) {
    String reason = normalize(raw);
    if (reason == null) {
      if (action == RecommendationFeedbackAction.APPROVE) {
        return "RELEVANT";
      }
      throw new IllegalArgumentException("reason is required for " + action.name());
    }
    String normalized = reason.toUpperCase(Locale.ROOT);
    if (!FEEDBACK_REASONS.get(action).contains(normalized)) {
      throw new IllegalArgumentException(
          "Unsupported reason for " + action.name() + ": " + reason
      );
    }
    return normalized;
  }

  private Mono<Integer> extractAndUpsert(RecommendationAttachmentSourceRow source) {
    RecommendationDataset dataset = mapDataset(source.sourceType());
    RecommendationFeatureRow extracted = featureExtractor.extract(source, dataset, Instant.now());
    return repository.upsertByAttachment(extracted)
        .thenReturn(1)
        .onErrorResume(error -> {
          meterRegistry.counter(
              "akcp.recommendations.backfill.row.total",
              "result",
              "error",
              "dataset",
              dataset.name(),
              "errorType",
              error.getClass().getSimpleName()
          ).increment();
          log.warn("Recommendation backfill failed for attachment={}", source.attachmentId(), error);
          return Mono.just(0);
        })
        .doOnSuccess(saved -> meterRegistry.counter(
            "akcp.recommendations.backfill.row.total",
            "result",
            saved == 1 ? "upserted" : "skipped",
            "dataset",
            dataset.name()
        ).increment());
  }

  private RecommendationDataset mapDataset(String sourceType) {
    if (sourceType == null) {
      return RecommendationDataset.CANDIDATE;
    }
    String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
    if ("VK".equals(normalized)) {
      return RecommendationDataset.VK_WALL;
    }
    return RecommendationDataset.CANDIDATE;
  }

  private Mono<Void> storeServingEvent(
      String username,
      UUID referenceAttachmentId,
      String experimentGroup,
      RecommendationTopResponse response,
      long startedNanos
  ) {
    final String candidatesJson;
    try {
      candidatesJson = objectMapper.writeValueAsString(new RecommendationRunSnapshot(
          response.candidates(),
          response.exclusions()
      ));
    } catch (JsonProcessingException ex) {
      return Mono.error(new IllegalStateException(
          "Unable to serialize recommendation snapshot",
          ex
      ));
    }
    RecommendationServingEventRow row = new RecommendationServingEventRow(
        response.runId(),
        username,
        referenceAttachmentId,
        experimentGroup,
        response.requestedLimit(),
        response.returnedCount(),
        candidatesJson,
        toMillis(startedNanos),
        Instant.now()
    );
    return repository.insertServingEvent(row)
        .doOnSuccess(ignored -> meterRegistry.counter(
            "akcp.recommendations.serving.total",
            "result",
            "success",
            "experiment",
            experimentGroup
        ).increment())
        .then();
  }

  private RecommendationTopResponse buildTop(
      RecommendationFeatureRow reference,
      List<RecommendationFeatureRow> candidates,
      List<RecommendationFeatureRow> history,
      RecommendationItemProfileRow referenceProfile,
      Map<UUID, RecommendationItemProfileRow> candidateProfiles,
      UUID referenceAttachmentId,
      int limit,
      String experimentGroup,
      UUID runId
  ) {
    if (isLegacyFeature(reference)) {
      throw new IllegalArgumentException(
          "Reference image has no versioned byte-analysis features"
      );
    }
    if (isTextDominant(reference)) {
      throw new IllegalArgumentException(
          "Reference image is text-dominant and cannot be used for recommendations"
      );
    }

    history = history.stream()
        .filter(historyRow -> !isLegacyFeature(historyRow) && !isTextDominant(historyRow))
        .toList();
    Map<UUID, double[]> embeddingCache = new HashMap<>();
    double[] historyCentroid = buildCentroid(history, embeddingCache);
    Set<String> historySha = buildHistorySha(history);
    List<Long> historyPhash = buildHistoryPhash(history);
    ScoringWeights weights = resolveWeights(experimentGroup);

    List<ScoredCandidate> scored = new ArrayList<>();
    List<RecommendationFeatureRow> candidateDedupRepresentatives = new ArrayList<>();
    List<RecommendationExclusionResponse> exclusions = new ArrayList<>();
    List<RecommendationFeatureRow> orderedCandidates = candidates.stream()
        .sorted((left, right) -> String.valueOf(left.attachmentId())
            .compareTo(String.valueOf(right.attachmentId())))
        .toList();
    for (RecommendationFeatureRow candidate : orderedCandidates) {
      if (candidate.attachmentId() == null) {
        continue;
      }
      if (isLegacyFeature(candidate)) {
        exclusions.add(exclusion(
            candidate,
            "MISSING_BYTE_ANALYSIS",
            "analysisVersion=" + candidate.analysisVersion(),
            null
        ));
        continue;
      }
      if (isTextDominant(candidate)) {
        exclusions.add(exclusion(
            candidate,
            "TEXT_DOMINANT",
            "textRatio=" + candidate.textRatio(),
            candidate.textRatio()
        ));
        continue;
      }
      if (isDuplicate(reference, candidate)) {
        exclusions.add(exclusion(
            candidate,
            "DUPLICATE_REFERENCE",
            duplicateEvidence(reference, candidate),
            (double) PHASH_NEAR_DUP_THRESHOLD
        ));
        continue;
      }
      if (isDuplicateAgainstHistory(candidate, historySha, historyPhash)) {
        exclusions.add(exclusion(
            candidate,
            "DUPLICATE_PUBLISHED_HISTORY",
            "sha256-match or phash-distance<=" + PHASH_NEAR_DUP_THRESHOLD,
            (double) PHASH_NEAR_DUP_THRESHOLD
        ));
        continue;
      }

      RecommendationItemProfileRow candidateProfile = candidateProfiles.get(candidate.attachmentId());
      double visualScore = visualSimilarity(reference, candidate, embeddingCache);
      if (visualScore <= 0.0d) {
        exclusions.add(exclusion(
            candidate,
            "MISSING_COMPATIBLE_VISUAL_SIGNAL",
            "no compatible embedding dimension or perceptual hash",
            null
        ));
        continue;
      }
      RecommendationFeatureRow duplicateCandidate = candidateDedupRepresentatives.stream()
          .filter(existing -> isDuplicate(existing, candidate))
          .findFirst()
          .orElse(null);
      if (duplicateCandidate != null) {
        exclusions.add(exclusion(
            candidate,
            "DUPLICATE_CANDIDATE",
            "representativeAttachmentId=" + duplicateCandidate.attachmentId()
                + "; " + duplicateEvidence(duplicateCandidate, candidate),
            (double) PHASH_NEAR_DUP_THRESHOLD
        ));
        continue;
      }
      candidateDedupRepresentatives.add(candidate);
      double historyScore = historySimilarity(candidate, historyCentroid, embeddingCache);
      double profileScore = profileSimilarity(referenceProfile, candidateProfile);
      double finalScore = (weights.visual() * visualScore)
          + (weights.history() * historyScore)
          + (weights.profile() * profileScore);

      String reason = buildReason(historyScore, profileScore, weights);
      scored.add(new ScoredCandidate(candidate, finalScore, visualScore, historyScore, profileScore, reason));
    }

    List<ScoredCandidate> selected = selectWithDiversity(scored, limit, embeddingCache);
    List<RecommendationCandidateResponse> response = new ArrayList<>();
    for (int index = 0; index < selected.size(); index++) {
      ScoredCandidate candidate = selected.get(index);
      double diversityPenalty = maxSimilarityToSelected(
          candidate.row(),
          selected.subList(0, index),
          embeddingCache
      );
      response.add(new RecommendationCandidateResponse(
          candidate.row().attachmentId(),
          candidate.row().itemId(),
          candidate.row().imageUrl(),
          round4(candidate.score()),
          round4(candidate.visualScore()),
          round4(candidate.historyScore()),
          candidate.reason(),
          index + 1,
          round4(diversityPenalty),
          new RecommendationExplanationResponse(
              candidate.row().analysisVersion() == null
                  ? "legacy-unknown"
                  : candidate.row().analysisVersion(),
              RANKING_VERSION,
              round4(weights.visual()),
              round4(weights.history()),
              round4(weights.profile()),
              round4(candidate.visualScore()),
              round4(candidate.historyScore()),
              round4(candidate.profileScore()),
              round4(diversityPenalty),
              nearestHistoryExemplars(candidate.row(), history, embeddingCache),
              "text-dominant-excluded; threshold=" + properties.getTextDominantThreshold()
          )
      ));
    }

    return new RecommendationTopResponse(
        referenceAttachmentId,
        limit,
        response.size(),
        response,
        runId,
        RANKING_VERSION,
        exclusions
    );
  }

  private RecommendationExclusionResponse exclusion(
      RecommendationFeatureRow candidate,
      String rule,
      String evidence,
      Double threshold
  ) {
    return new RecommendationExclusionResponse(
        candidate.attachmentId(),
        rule,
        candidate.analysisVersion() == null ? "legacy-unknown" : candidate.analysisVersion(),
        evidence,
        threshold
    );
  }

  private String duplicateEvidence(
      RecommendationFeatureRow left,
      RecommendationFeatureRow right
  ) {
    String leftSha = normalizeSha256(left.sha256());
    String rightSha = normalizeSha256(right.sha256());
    if (leftSha != null && leftSha.equals(rightSha)) {
      return "exact-sha256-match";
    }
    if (left.phash() != null && right.phash() != null) {
      return "phashDistance=" + phashDistance(left.phash(), right.phash());
    }
    return "duplicate-rule-match";
  }

  private ScoringWeights resolveWeights(String experimentGroup) {
    double visual = properties.getVisualWeight();
    double history = properties.getHistoryWeight();
    double profile = properties.getProfileWeight();
    if ("visual_history_v1".equalsIgnoreCase(experimentGroup)) {
      profile = 0.0d;
    }
    double total = visual + history + profile;
    if (total <= 0.0d) {
      return new ScoringWeights(1.0d, 0.0d, 0.0d);
    }
    return new ScoringWeights(visual / total, history / total, profile / total);
  }

  private String buildReason(double historyScore, double profileScore, ScoringWeights weights) {
    List<String> parts = new ArrayList<>();
    parts.add("visual-similarity");
    if (weights.history() > 0.0d && historyScore > 0.0d) {
      parts.add("history-profile");
    }
    if (weights.profile() > 0.0d && profileScore > 0.0d) {
      parts.add("source-tags-time");
    }
    return String.join(" + ", parts);
  }

  private List<ScoredCandidate> selectWithDiversity(
      List<ScoredCandidate> scored,
      int limit,
      Map<UUID, double[]> embeddingCache
  ) {
    List<ScoredCandidate> pool = new ArrayList<>(scored);
    List<ScoredCandidate> selected = new ArrayList<>();
    while (!pool.isEmpty() && selected.size() < limit) {
      ScoredCandidate best = null;
      double bestUtility = Double.NEGATIVE_INFINITY;
      for (ScoredCandidate candidate : pool) {
        double penalty = maxSimilarityToSelected(candidate.row(), selected, embeddingCache);
        double utility = (0.8d * candidate.score()) - (0.2d * penalty);
        if (utility > bestUtility
            || (Double.compare(utility, bestUtility) == 0
                && compareCandidateIdentity(candidate, best) < 0)) {
          bestUtility = utility;
          best = candidate;
        }
      }
      if (best == null) {
        break;
      }
      selected.add(best);
      pool.remove(best);
    }
    return selected;
  }

  private int compareCandidateIdentity(ScoredCandidate left, ScoredCandidate right) {
    if (right == null) {
      return -1;
    }
    return String.valueOf(left.row().attachmentId())
        .compareTo(String.valueOf(right.row().attachmentId()));
  }

  private double maxSimilarityToSelected(
      RecommendationFeatureRow candidate,
      List<ScoredCandidate> selected,
      Map<UUID, double[]> embeddingCache
  ) {
    if (selected.isEmpty()) {
      return 0.0d;
    }
    double max = 0.0d;
    for (ScoredCandidate existing : selected) {
      max = Math.max(max, rowSimilarity(candidate, existing.row(), embeddingCache));
    }
    return max;
  }

  private List<UUID> nearestHistoryExemplars(
      RecommendationFeatureRow candidate,
      List<RecommendationFeatureRow> history,
      Map<UUID, double[]> embeddingCache
  ) {
    return history.stream()
        .filter(row -> row.attachmentId() != null)
        .filter(row -> analysisVersionsCompatible(candidate, row))
        .filter(row -> rowSimilarity(candidate, row, embeddingCache) > 0.0d)
        .sorted((left, right) -> {
          int bySimilarity = Double.compare(
              rowSimilarity(candidate, right, embeddingCache),
              rowSimilarity(candidate, left, embeddingCache)
          );
          if (bySimilarity != 0) {
            return bySimilarity;
          }
          return String.valueOf(left.attachmentId())
              .compareTo(String.valueOf(right.attachmentId()));
        })
        .limit(3)
        .map(RecommendationFeatureRow::attachmentId)
        .toList();
  }

  private boolean analysisVersionsCompatible(
      RecommendationFeatureRow left,
      RecommendationFeatureRow right
  ) {
    String leftVersion = normalize(left.analysisVersion());
    String rightVersion = normalize(right.analysisVersion());
    return leftVersion != null && leftVersion.equals(rightVersion);
  }

  private boolean isDuplicateAgainstHistory(RecommendationFeatureRow candidate, Set<String> historySha, List<Long> historyPhash) {
    String sha = normalizeSha256(candidate.sha256());
    if (sha != null && historySha.contains(sha)) {
      return true;
    }
    if (candidate.phash() == null) {
      return false;
    }
    for (Long history : historyPhash) {
      if (history != null && phashDistance(candidate.phash(), history) <= PHASH_NEAR_DUP_THRESHOLD) {
        return true;
      }
    }
    return false;
  }

  private boolean isDuplicate(RecommendationFeatureRow reference, RecommendationFeatureRow candidate) {
    String refSha = normalizeSha256(reference.sha256());
    String candidateSha = normalizeSha256(candidate.sha256());
    if (refSha != null && candidateSha != null && refSha.equals(candidateSha)) {
      return true;
    }
    if (reference.phash() != null && candidate.phash() != null) {
      return phashDistance(reference.phash(), candidate.phash()) <= PHASH_NEAR_DUP_THRESHOLD;
    }
    return false;
  }

  private Set<String> buildHistorySha(List<RecommendationFeatureRow> history) {
    Set<String> values = new HashSet<>();
    for (RecommendationFeatureRow row : history) {
      String normalized = normalizeSha256(row.sha256());
      if (normalized != null) {
        values.add(normalized);
      }
    }
    return values;
  }

  private List<Long> buildHistoryPhash(List<RecommendationFeatureRow> history) {
    List<Long> values = new ArrayList<>(history.size());
    for (RecommendationFeatureRow row : history) {
      if (row.phash() != null) {
        values.add(row.phash());
      }
    }
    return values;
  }

  private double[] buildCentroid(List<RecommendationFeatureRow> rows, Map<UUID, double[]> embeddingCache) {
    double[] sum = null;
    int count = 0;
    for (RecommendationFeatureRow row : rows) {
      double[] vector = embeddingOf(row, embeddingCache);
      if (vector == null || vector.length == 0) {
        continue;
      }
      if (sum == null) {
        sum = new double[vector.length];
      }
      if (sum.length != vector.length) {
        continue;
      }
      for (int i = 0; i < vector.length; i++) {
        sum[i] += vector[i];
      }
      count++;
    }
    if (sum == null || count == 0) {
      return null;
    }
    for (int i = 0; i < sum.length; i++) {
      sum[i] /= count;
    }
    return sum;
  }

  private double visualSimilarity(
      RecommendationFeatureRow reference,
      RecommendationFeatureRow candidate,
      Map<UUID, double[]> embeddingCache
  ) {
    double[] ref = embeddingOf(reference, embeddingCache);
    double[] cand = embeddingOf(candidate, embeddingCache);
    if (ref != null && cand != null && ref.length == cand.length) {
      return normalizedCosine(ref, cand);
    }
    if (reference.phash() != null && candidate.phash() != null) {
      return phashSimilarity(reference.phash(), candidate.phash());
    }
    return 0.0d;
  }

  private double historySimilarity(
      RecommendationFeatureRow candidate,
      double[] historyCentroid,
      Map<UUID, double[]> embeddingCache
  ) {
    if (historyCentroid == null) {
      return 0.0d;
    }
    double[] candidateEmbedding = embeddingOf(candidate, embeddingCache);
    if (candidateEmbedding == null || candidateEmbedding.length != historyCentroid.length) {
      return 0.0d;
    }
    return normalizedCosine(candidateEmbedding, historyCentroid);
  }

  private double profileSimilarity(RecommendationItemProfileRow reference, RecommendationItemProfileRow candidate) {
    if (reference == null || candidate == null) {
      return 0.0d;
    }
    double sourceScore = sourceTypeScore(reference.sourceType(), candidate.sourceType());
    double tagScore = jaccard(reference.tags(), candidate.tags());
    double timeScore = recencyScore(reference.itemCreatedAt(), candidate.itemCreatedAt());
    return (0.45d * sourceScore) + (0.40d * tagScore) + (0.15d * timeScore);
  }

  private double sourceTypeScore(String referenceSourceType, String candidateSourceType) {
    if (referenceSourceType == null || candidateSourceType == null) {
      return 0.0d;
    }
    return referenceSourceType.equalsIgnoreCase(candidateSourceType) ? 1.0d : 0.0d;
  }

  private double jaccard(List<String> left, List<String> right) {
    if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
      return 0.0d;
    }
    Set<String> a = new HashSet<>();
    for (String value : left) {
      if (value != null && !value.isBlank()) {
        a.add(value.toLowerCase(Locale.ROOT));
      }
    }
    Set<String> b = new HashSet<>();
    for (String value : right) {
      if (value != null && !value.isBlank()) {
        b.add(value.toLowerCase(Locale.ROOT));
      }
    }
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0d;
    }
    Set<String> intersection = new HashSet<>(a);
    intersection.retainAll(b);
    if (intersection.isEmpty()) {
      return 0.0d;
    }
    Set<String> union = new HashSet<>(a);
    union.addAll(b);
    return (double) intersection.size() / (double) union.size();
  }

  private double recencyScore(Instant referenceCreatedAt, Instant candidateCreatedAt) {
    if (referenceCreatedAt == null || candidateCreatedAt == null) {
      return 0.0d;
    }
    long days = Math.abs(Duration.between(referenceCreatedAt, candidateCreatedAt).toDays());
    return 1.0d / (1.0d + (days / 30.0d));
  }

  private double rowSimilarity(
      RecommendationFeatureRow left,
      RecommendationFeatureRow right,
      Map<UUID, double[]> embeddingCache
  ) {
    double[] a = embeddingOf(left, embeddingCache);
    double[] b = embeddingOf(right, embeddingCache);
    if (a != null && b != null && a.length == b.length) {
      return normalizedCosine(a, b);
    }
    if (left.phash() != null && right.phash() != null) {
      return phashSimilarity(left.phash(), right.phash());
    }
    return 0.0d;
  }

  private double[] embeddingOf(RecommendationFeatureRow row, Map<UUID, double[]> cache) {
    if (row == null || row.id() == null || row.embeddingJson() == null || row.embeddingJson().isBlank()) {
      return null;
    }
    if (cache.containsKey(row.id())) {
      return cache.get(row.id());
    }
    try {
      List<Double> values = objectMapper.readValue(row.embeddingJson(), new TypeReference<List<Double>>() {
      });
      double[] vector = new double[values.size()];
      for (int i = 0; i < values.size(); i++) {
        vector[i] = values.get(i) == null ? 0.0d : values.get(i);
      }
      cache.put(row.id(), vector);
      return vector;
    } catch (Exception ex) {
      cache.put(row.id(), null);
      return null;
    }
  }

  private double normalizedCosine(double[] a, double[] b) {
    double dot = 0.0d;
    double normA = 0.0d;
    double normB = 0.0d;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0.0d || normB == 0.0d) {
      return 0.0d;
    }
    double cosine = dot / (Math.sqrt(normA) * Math.sqrt(normB));
    double normalized = (cosine + 1.0d) / 2.0d;
    if (normalized < 0.0d) {
      return 0.0d;
    }
    return Math.min(normalized, 1.0d);
  }

  private double phashSimilarity(long left, long right) {
    int distance = phashDistance(left, right);
    return 1.0d - (Math.min(distance, 64) / 64.0d);
  }

  private int phashDistance(long left, long right) {
    return Long.bitCount(left ^ right);
  }

  private int normalizeLimit(Integer limitRaw) {
    int value = limitRaw == null ? DEFAULT_LIMIT : limitRaw;
    if (value <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(value, MAX_LIMIT);
  }

  private int normalizeBackfillLimit(Integer limitRaw) {
    int configured = properties.getBackfillBatchSize();
    int value = limitRaw == null ? configured : limitRaw;
    if (value <= 0) {
      return configured;
    }
    return Math.min(value, MAX_BACKFILL_LIMIT);
  }

  private String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    return value.isEmpty() ? null : value;
  }

  private boolean isTextDominant(RecommendationFeatureRow row) {
    return row.textDominant()
        || (row.textRatio() != null
        && row.textRatio() >= properties.getTextDominantThreshold());
  }

  private boolean isLegacyFeature(RecommendationFeatureRow row) {
    return row.analysisVersion() == null || LEGACY_URL_ANALYSIS.equals(row.analysisVersion());
  }

  private String normalizeSha256(String raw) {
    String value = normalize(raw);
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  private void validateManualFeature(RecommendationFeatureUpsertRequest request) {
    String sha256 = normalizeSha256(request.sha256());
    if (sha256 != null && !sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
    }
    Double textRatio = request.textRatio();
    if (textRatio != null
        && (!Double.isFinite(textRatio) || textRatio < 0.0d || textRatio > 1.0d)) {
      throw new IllegalArgumentException("textRatio must be finite and between 0 and 1");
    }
    List<Double> embedding = request.embedding();
    if (embedding == null) {
      return;
    }
    if (embedding.isEmpty() || embedding.size() > 4096) {
      throw new IllegalArgumentException("embedding must contain between 1 and 4096 values");
    }
    for (Double value : embedding) {
      if (value == null || !Double.isFinite(value)) {
        throw new IllegalArgumentException("embedding values must be finite");
      }
    }
  }

  private Mono<Void> rejectCanonicalOverwrite(RecommendationFeatureRow existing) {
    if (isVersionedAnalysis(existing.analysisVersion())) {
      return Mono.error(new IllegalArgumentException(
          "Versioned byte-analysis features cannot be overwritten by the manual feature API"
      ));
    }
    return Mono.empty();
  }

  private boolean isVersionedAnalysis(String analysisVersion) {
    return analysisVersion != null
        && !analysisVersion.isBlank()
        && !LEGACY_URL_ANALYSIS.equals(analysisVersion)
        && !"manual-feature-v1".equals(analysisVersion);
  }

  private long toMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }

  private double round4(double value) {
    return Math.round(value * 10_000d) / 10_000d;
  }

  private record ScoredCandidate(
      RecommendationFeatureRow row,
      double score,
      double visualScore,
      double historyScore,
      double profileScore,
      String reason
  ) {
  }

  private record ScoringWeights(double visual, double history, double profile) {
  }

  private record RecommendationRunSnapshot(
      List<RecommendationCandidateResponse> candidates,
      List<RecommendationExclusionResponse> exclusions
  ) {
  }
}
