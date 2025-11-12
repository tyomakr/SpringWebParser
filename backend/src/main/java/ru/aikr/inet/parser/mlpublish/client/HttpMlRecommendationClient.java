package ru.aikr.inet.parser.mlpublish.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
public class HttpMlRecommendationClient implements MlRecommendationClient {

    private final WebClient webClient;
    private final MlRecommendationProperties properties;

    public HttpMlRecommendationClient(WebClient webClient, MlRecommendationProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Mono<List<MlRecommendation>> recommend(List<WebImage> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.warn("ML publish API key is not configured, skipping ML recommendations");
            return Mono.just(Collections.emptyList());
        }

        final String apiKey = Objects.requireNonNull(
                properties.getApiKey(),
                "ml.publish.api-key must not be null"
        );

        MlRecommendationRequest request = new MlRecommendationRequest(
                candidates.stream()
                        .map(img -> new MlRecommendationRequest.MlRecommendationCandidate(
                                img.getId(), img.getDirectLink()))
                        .collect(Collectors.toUnmodifiableList())
        );

        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        String path = Objects.requireNonNull(
                properties.getRecommendationPath(),
                "ml.publish.recommendation-path must not be null"
        );

        return webClient.post()
                .uri(path)
                .headers(h -> h.setBearerAuth(apiKey))
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("empty")
                                .flatMap(body -> Mono.error(new MlRecommendationUnauthorizedException(
                                        "ML service responded with 401 Unauthorized: " + body
                                )))
                )
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("empty")
                                .flatMap(body -> Mono.error(new MlRecommendationException(
                                        "ML service responded with error: " +
                                                response.statusCode() + " - " + body
                                )))
                )
                .bodyToMono(MlRecommendationResponse.class)
                .timeout(timeout)
                .map(this::mapResponse)
                .doOnError(error -> {
                    if (!shouldFallback(error)) {
                        log.warn("Failed to call ML recommendation service", error);
                    }
                })
                .onErrorResume(error -> {
                    if (shouldFallback(error)) {
                        log.warn("ML recommendation fallback triggered: {}", error.getMessage());
                        return Mono.just(Collections.emptyList());
                    }
                    if (error instanceof MlRecommendationException) {
                        return Mono.error(error);
                    }
                    return Mono.error(new MlRecommendationException("Failed to fetch ML recommendations", error));
                });
    }

    private List<MlRecommendation> mapResponse(MlRecommendationResponse response) {
        if (response == null || response.recommendations() == null) {
            throw new MlRecommendationException("ML service returned empty payload");
        }
        return response.recommendations().stream()
                .map(this::mapItem)
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

    private static boolean shouldFallback(Throwable error) {
        return error instanceof MlRecommendationUnauthorizedException || error instanceof TimeoutException;
    }
}
