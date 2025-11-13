package ru.aikr.inet.parser.mlpublish.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.model.MlClientConfigResponse;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;
import ru.aikr.inet.parser.mlpublish.model.MlMetricsResponse;
import ru.aikr.inet.parser.mlpublish.model.MlStatusResponse;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ml")
public class MlStatusController {

    private final MlConfigClient configClient;
    private final MlRecommendationProperties properties;
    @Qualifier("mlStatusWebClient")
    private final WebClient statusWebClient;

    @GetMapping("/config")
    public Mono<MlClientConfigResponse> config() {
        boolean configured = StringUtils.hasText(properties.getApiKey());
        return configClient.config()
                .map(mlService -> new MlClientConfigResponse(
                        configured,
                        properties.isRequireApiKey(),
                        properties.getMaxBatchSize(),
                        mlService
                ));
    }

    @GetMapping("/status")
    public Mono<MlStatusResponse> status() {
        Duration timeout = Duration.ofSeconds(Math.max(2, properties.getTimeoutSeconds()));
        Mono<MlMetricsResponse> metricsMono = statusWebClient.get()
                .uri("/metrics")
                .retrieve()
                .bodyToMono(MlMetricsResponse.class)
                .timeout(timeout);
        Mono<MlConfigResponse> configMono = statusWebClient.get()
                .uri("/config")
                .retrieve()
                .bodyToMono(MlConfigResponse.class)
                .timeout(timeout);
        return Mono.zip(metricsMono, configMono)
                .map(tuple -> new MlStatusResponse(true, tuple.getT1().indexSize(), tuple.getT2(), null))
                .onErrorResume(error -> Mono.just(new MlStatusResponse(false, null, null, deriveError(error))));
    }

    private String deriveError(Throwable error) {
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException responseException) {
            return String.format("ml service responded with %s", responseException.getStatusCode());
        }
        if (error instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        if (error instanceof Exception) {
            return error.getMessage();
        }
        return HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase();
    }
}
