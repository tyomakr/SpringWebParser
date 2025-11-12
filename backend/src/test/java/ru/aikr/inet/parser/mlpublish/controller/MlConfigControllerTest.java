package ru.aikr.inet.parser.mlpublish.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WebFluxTest(MlConfigController.class)
@Import(MlConfigControllerTest.TestConfig.class)
class MlConfigControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void configReturnsValues() {
        webTestClient.get().uri("/api/ml/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.phashMaxDist").isEqualTo(10)
                .jsonPath("$.grayBand").isEqualTo(4);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MlConfigClient configClient() {
            MlConfigClient client = mock(MlConfigClient.class);
            when(client.config()).thenReturn(Mono.just(new MlConfigResponse(10, 4)));
            return client;
        }
    }
}
