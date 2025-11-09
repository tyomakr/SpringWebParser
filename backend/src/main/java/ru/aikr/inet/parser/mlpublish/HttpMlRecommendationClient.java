package ru.aikr.inet.parser.mlpublish;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.RecommendationDecision;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

        MlRecommendationRequest request = new MlRecommendationRequest(
                candidates.stream()
                        .map(image -> new MlRecommendationRequest.MlRecommendationCandidate(image.getId(), image.getDirectLink()))
                        .collect(Collectors.toUnmodifiableList())
        );

        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());

        return webClient.post()
                .uri(properties.getRecommendationPath())
                .bodyValue(request)
                .retrieve()
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
                .doOnError(error -> log.warn("Failed to call ML recommendation service", error))
                .onErrorMap(error -> error instanceof MlRecommendationException ? error :
                        new MlRecommendationException("Failed to fetch ML recommendations", error));
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
                toDecision(item.decision())
        );
    }

    private RecommendationDecision toDecision(String token) {
        if (token == null) {
            return RecommendationDecision.SKIP;
        }
        try {
            return RecommendationDecision.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown ML recommendation '{}', defaulting to SKIP", token);
            return RecommendationDecision.SKIP;
        }
    }
}
