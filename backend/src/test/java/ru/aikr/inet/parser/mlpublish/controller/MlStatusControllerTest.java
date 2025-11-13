package ru.aikr.inet.parser.mlpublish.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;
import ru.aikr.inet.parser.mlpublish.model.MlMetricsResponse;
import ru.aikr.inet.parser.mlpublish.model.MlStatusResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WebFluxTest(MlStatusController.class)
@Import(MlStatusControllerTest.TestConfig.class)
class MlStatusControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MlConfigClient configClient;

    @Autowired
    private TestConfig testConfig;

    @BeforeEach
    void setup() {
        when(configClient.config()).thenReturn(Mono.just(new MlConfigResponse(10, 4)));
    }

    @Test
    void configReturnsCombinedValues() {
        when(configClient.config()).thenReturn(Mono.just(new MlConfigResponse(10, 4)));

        webTestClient.get().uri("/api/ml/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.apiKeyConfigured").isEqualTo(true)
                .jsonPath("$.requireApiKey").isEqualTo(false)
                .jsonPath("$.maxBatchSize").isEqualTo(100)
                .jsonPath("$.mlServiceConfig.phashMaxDist").isEqualTo(10)
                .jsonPath("$.mlServiceConfig.grayBand").isEqualTo(4);
    }

    @Test
    void statusReturnsMetricsAndConfig() {
        testConfig.setExchangeFunction(request -> {
            if (request.url().getPath().endsWith("/metrics")) {
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .body(new MlMetricsResponse(5))
                                .build());
            }
            return Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .body(new MlConfigResponse(12, 3))
                            .build());
        });

        webTestClient.get().uri("/api/ml/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mlReachable").isEqualTo(true)
                .jsonPath("$.indexSize").isEqualTo(5)
                .jsonPath("$.config.phashMaxDist").isEqualTo(12);
    }

    @Test
    void statusReportsUnreachableOnTimeout() {
        testConfig.setExchangeFunction(request -> Mono.error(new RuntimeException("timeout")));

        webTestClient.get().uri("/api/ml/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mlReachable").isEqualTo(false)
                .jsonPath("$.error").isEqualTo("timeout");
    }

    @TestConfiguration
    static class TestConfig {

        private final AtomicReference<ExchangeFunction> delegate = new AtomicReference<>(
                request -> Mono.error(new IllegalStateException("no handler set")));

        @Bean
        MlRecommendationProperties properties() {
            MlRecommendationProperties properties = new MlRecommendationProperties();
            properties.setBaseUrl("http://ml-service");
            properties.setApiKey("dummy");
            properties.setMaxBatchSize(100);
            properties.setRequireApiKey(false);
            properties.setTimeoutSeconds(2);
            return properties;
        }

        @Bean
        MlConfigClient configClient() {
            MlConfigClient client = mock(MlConfigClient.class);
            when(client.config()).thenReturn(Mono.just(new MlConfigResponse(10, 4)));
            return client;
        }

        @Bean
        @Qualifier("mlStatusWebClient")
        WebClient mlStatusWebClient(ExchangeFunction exchangeFunction, MlRecommendationProperties properties) {
            return WebClient.builder()
                    .exchangeFunction(exchangeFunction)
                    .baseUrl(properties.getBaseUrl())
                    .build();
        }

        @Bean
        ExchangeFunction exchangeFunction() {
            return request -> delegate.get().exchange(request);
        }

        void setExchangeFunction(ExchangeFunction exchangeFunction) {
            delegate.set(exchangeFunction);
        }
    }
}
