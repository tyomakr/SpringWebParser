package ru.aikr.inet.parser.mlpublish.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.model.MlClientConfigResponse;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;
import ru.aikr.inet.parser.mlpublish.model.MlMetricsResponse;
import ru.aikr.inet.parser.mlpublish.model.MlOcrDiagnosticsResponse;
import ru.aikr.inet.parser.mlpublish.model.MlStatusResponse;

import java.time.Duration;

@RestController
@RequestMapping("/api/ml")
public class MlStatusController {

    private final MlConfigClient configClient;
    private final MlRecommendationProperties properties;
    @Qualifier("mlStatusWebClient")
    private final WebClient statusWebClient;

    public MlStatusController(MlConfigClient configClient,
                              MlRecommendationProperties properties,
                              @Qualifier("mlStatusWebClient") WebClient statusWebClient) {
        this.configClient = configClient;
        this.properties = properties;
        this.statusWebClient = statusWebClient;
    }

    @GetMapping("/config")
    public Mono<MlClientConfigResponse> config() {
        boolean configured = StringUtils.hasText(properties.getApiKey());
        return configClient.config()
                .map(mlService -> new MlClientConfigResponse(
                        configured,
                        properties.isRequireApiKey(),
                        properties.getMaxBatchSize(),
                        mlService
                ))
                .onErrorResume(error -> Mono.just(new MlClientConfigResponse(
                        configured,
                        properties.isRequireApiKey(),
                        properties.getMaxBatchSize(),
                        null
                )));
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

    @GetMapping("/ocr-diagnostics")
    public Mono<MlOcrDiagnosticsResponse> ocrDiagnostics() {
        Duration timeout = diagnosticsTimeout();
        return statusWebClient.get()
                .uri("/ocr/diagnostics")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(report -> new MlOcrDiagnosticsResponse(true, report, null))
                .onErrorResume(error -> Mono.just(new MlOcrDiagnosticsResponse(false, null, deriveError(error))));
    }

    @PostMapping("/ocr-diagnostics/run")
    public Mono<MlOcrDiagnosticsResponse> runOcrDiagnostics(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        Duration timeout = diagnosticsTimeout();
        return statusWebClient.post()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/ocr/diagnostics/run");
                    if (limit != null) {
                        builder.queryParam("limit", limit);
                    }
                    if (offset != null) {
                        builder.queryParam("offset", offset);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(timeout)
                .map(report -> new MlOcrDiagnosticsResponse(true, report, null))
                .onErrorResume(error -> Mono.just(new MlOcrDiagnosticsResponse(false, null, deriveError(error))));
    }

    private Duration diagnosticsTimeout() {
        return Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds()));
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
