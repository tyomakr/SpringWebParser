package ru.aikr.inet.parser.recommendation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
public class HttpRecommendationClient implements RecommendationClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);

    private final WebClient webClient;

    public HttpRecommendationClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public List<RecommendationResult> recommend(List<WebImage> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        RecommendationRequest request = new RecommendationRequest(
                candidates.stream()
                        .map(image -> new RecommendationRequest.RecommendationInput(
                                image.getId(),
                                image.getDirectLink()
                        ))
                        .collect(Collectors.toUnmodifiableList())
        );

        RecommendationResponse response = webClient.post()
                .uri("/recommend")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("empty")
                                .flatMap(body -> Mono.error(new RecommendationException(
                                        "ML service responded with error: " +
                                                clientResponse.statusCode() + " - " + body
                                )))
                )
                .bodyToMono(RecommendationResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .doOnError(error -> log.warn("Failed to call recommendation service", error))
                .block();

        if (response == null || response.recommendations() == null) {
            throw new RecommendationException("ML service returned empty payload");
        }

        return response.recommendations().stream()
                .map(this::mapItem)
                .collect(Collectors.toUnmodifiableList());
    }

    private RecommendationResult mapItem(RecommendationResponse.RecommendationItem item) {
        RecommendationDecision decision = toDecision(item.recommendation());
        return new RecommendationResult(item.id(), item.url(), item.score(), item.reason(), decision);
    }

    private RecommendationDecision toDecision(String token) {
        if (token == null) {
            return RecommendationDecision.SKIP;
        }
        try {
            return RecommendationDecision.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown recommendation '{}' from ML service, defaulting to SKIP", token);
            return RecommendationDecision.SKIP;
        }
    }
}