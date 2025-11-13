package ru.aikr.inet.parser.mlpublish.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationException;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationUnauthorizedException;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendation;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendationRequest;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendationResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class HttpMlRecommendationClient implements MlRecommendationClient {

    private final WebClient webClient;
    private final MlRecommendationProperties properties;
    private static final String EMPTY_BODY = "empty";

    public HttpMlRecommendationClient(WebClient webClient, MlRecommendationProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Mono<List<MlRecommendation>> recommend(List<WebImage> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        String path = Objects.requireNonNull(
                properties.getRecommendationPath(),
                "ml.publish.recommendation-path must not be null"
        );
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        int chunkSize = Math.max(1, properties.getMaxBatchSize());
        List<CandidateWithIndex> indexedCandidates = IntStream.range(0, candidates.size())
                .mapToObj(i -> new CandidateWithIndex(candidates.get(i), i))
                .collect(Collectors.toUnmodifiableList());
        List<List<CandidateWithIndex>> chunks = partitionCandidates(indexedCandidates, chunkSize);

        return Flux.fromIterable(chunks)
                .concatMap(chunk -> requestChunk(path, chunk, timeout))
                .collectList()
                .map(listOfLists -> listOfLists.stream()
                        .flatMap(List::stream)
                        .sorted(Comparator.comparingInt(RecommendationWithIndex::index))
                        .map(RecommendationWithIndex::recommendation)
                        .collect(Collectors.toUnmodifiableList()))
                .doOnError(error -> {
                    if (!shouldFallback(error)) {
                        log.warn("Failed to call ML recommendation service", error);
                    }
                })
                .onErrorResume(this::handleErrorFallback);
    }

    private Mono<List<RecommendationWithIndex>> requestChunk(@NonNull String path,
                                                             List<CandidateWithIndex> chunk,
                                                             Duration timeout) {
        Objects.requireNonNull(path, "path must not be null");
        MlRecommendationRequest request = new MlRecommendationRequest(buildCandidates(chunk));
        Map<String, Integer> indexById = new HashMap<>();
        chunk.forEach(candidate -> {
            String id = Objects.requireNonNull(candidate.image().getId(), "candidate id");
            indexById.put(id, candidate.index());
        });

        return webClient.post()
                .uri(path)
                .headers(this::applyAuthorization)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), this::handleUnauthorized)
                .onStatus(HttpStatusCode::isError, this::handleHttpError)
                .bodyToMono(MlRecommendationResponse.class)
                .timeout(timeout)
                .map(response -> mapChunkResponse(response, indexById));
    }

    private List<MlRecommendationRequest.MlRecommendationCandidate> buildCandidates(
            List<CandidateWithIndex> chunk) {
        return chunk.stream()
                .map(candidate -> new MlRecommendationRequest.MlRecommendationCandidate(
                        Objects.requireNonNull(candidate.image().getId(), "candidate id"),
                        Objects.requireNonNull(candidate.image().getDirectLink(), "candidate url")))
                .collect(Collectors.toUnmodifiableList());
    }

    private void applyAuthorization(HttpHeaders headers) {
        String apiKey = properties.getApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(Objects.requireNonNull(apiKey, "apiKey must not be null"));
        }
    }

    private Mono<Throwable> handleUnauthorized(ClientResponse response) {
        return readBodySafely(response)
                .defaultIfEmpty(EMPTY_BODY)
                .map(body -> new MlRecommendationUnauthorizedException(
                        "ML service responded with 401 Unauthorized: " + body
                ));
    }

    private Mono<Throwable> handleHttpError(ClientResponse response) {
        return readBodySafely(response)
                .defaultIfEmpty(EMPTY_BODY)
                .map(body -> new MlRecommendationException(
                        "ML service responded with error: " +
                                response.statusCode() + " - " + body
                ));
    }

    private Mono<String> readBodySafely(ClientResponse response) {
        Mono<String> body = response.bodyToMono(String.class);
        return body != null ? body : Mono.just(EMPTY_BODY);
    }

    private List<RecommendationWithIndex> mapChunkResponse(MlRecommendationResponse response,
                                                           Map<String, Integer> indexMap) {
        if (response == null || response.recommendations() == null) {
            throw new MlRecommendationException("ML service returned empty payload");
        }
        return response.recommendations().stream()
                .map(this::mapItem)
                .map(rec -> new RecommendationWithIndex(rec,
                        indexMap.getOrDefault(rec.id(), Integer.MAX_VALUE)))
                .collect(Collectors.toUnmodifiableList());
    }

    private MlRecommendation mapItem(MlRecommendationResponse.MlRecommendationItem item) {
        return new MlRecommendation(
                item.id(),
                item.url(),
                item.score(),
                item.reason(),
                toDecision(item.decision()),
                item.zone(),
                item.hash()
        );
    }

    private RecommendationDecision toDecision(String token) {
        if (token == null) {
            return RecommendationDecision.SKIP;
        }
        try {
            return RecommendationDecision.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown ML recommendation '{}' , defaulting to SKIP", token);
            return RecommendationDecision.SKIP;
        }
    }

    private boolean shouldFallback(Throwable error) {
        if (error instanceof TimeoutException) {
            return true;
        }
        if (error instanceof MlRecommendationUnauthorizedException) {
            return properties.isRequireApiKey();
        }
        return false;
    }

    private Mono<List<MlRecommendation>> handleErrorFallback(Throwable error) {
        if (shouldFallback(error)) {
            log.warn("ML recommendation fallback triggered: {}", error.getMessage());
            return Mono.just(Collections.emptyList());
        }
        if (error instanceof MlRecommendationException) {
            return Mono.error(error);
        }
        return Mono.error(new MlRecommendationException("Failed to fetch ML recommendations", error));
    }

    private static List<List<CandidateWithIndex>> partitionCandidates(List<CandidateWithIndex> candidates,
                                                                      int chunkSize) {
        List<List<CandidateWithIndex>> result = new ArrayList<>();
        int size = candidates.size();
        for (int start = 0; start < size; start += chunkSize) {
            result.add(candidates.subList(start, Math.min(start + chunkSize, size)));
        }
        return result;
    }

    private record CandidateWithIndex(WebImage image, int index) {
    }

    private record RecommendationWithIndex(MlRecommendation recommendation, int index) {
    }
}
