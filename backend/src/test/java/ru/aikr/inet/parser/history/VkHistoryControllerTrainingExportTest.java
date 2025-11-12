package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.aikr.inet.parser.history.controller.VkHistoryController;
import ru.aikr.inet.parser.history.model.VkHistoryTrainingExportResponse;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.history.service.VkWallSyncService;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Если в проекте активна автонастройка Spring Security (по умолчанию в WebFluxTest — да),
// эта аннотация поднимет только контроллер VkHistoryController и базовые web-компоненты.
@WebFluxTest(controllers = VkHistoryController.class)
// Прокидываем тестовый API-ключ как application property, чтобы контроллер
// требовал заголовок Authorization для /training/export
@TestPropertySource(properties = "ml.publish.api-key=export-key")
// Подмешиваем тестовые бины: мок сервиса и мок паблишера логов (чтобы не падал контекст)
@Import(VkHistoryControllerTrainingExportTest.TestBeans.class)
class VkHistoryControllerTrainingExportTest {

    private static final String API_KEY = "export-key";

    @TestConfiguration
    static class TestBeans {

        @Bean
        VkHistoryService historyService() {
            return Mockito.mock(VkHistoryService.class);
        }

        @Bean
        VkWallSyncService wallSyncService() {
            return Mockito.mock(VkWallSyncService.class);
        }

        @Bean
        LogEventsPublisher logEventsPublisher() {
            // Нужен как заглушка, иначе приложение потребует реальный бин при старте контекста
            return Mockito.mock(LogEventsPublisher.class);
        }

        // Если у тебя подключён spring-security-webflux и тест внезапно начинает отдавать 401
        // до контроллера, раскомментируй блок ниже — он отключит security в этом тесте.
        /*
        @Bean
        @org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
        org.springframework.security.web.server.SecurityWebFilterChain securityChain(
                org.springframework.security.config.web.server.ServerHttpSecurity http
        ) {
            return http.csrf(csrf -> csrf.disable())
                       .authorizeExchange(ex -> ex.anyExchange().permitAll())
                       .build();
        }
        */
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private VkHistoryService historyService;

    @Autowired
    private LogEventsPublisher logEventsPublisher;

    @AfterEach
    void resetMocks() {
        Mockito.reset(historyService, logEventsPublisher);
    }

    @Test
    void shouldReturnTrainingExportData() {
        Instant since = Instant.parse("2024-01-01T00:00:00Z");
        VkHistoryTrainingExportResponse dto = new VkHistoryTrainingExportResponse(
                1L,
                "https://example.com/1.jpg",
                "hash-1",
                since,
                "PUBLISH",
                0.8,
                "reason"
        );
        when(historyService.exportTraining(50, 10, since)).thenReturn(List.of(dto));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/vk-history/training/export")
                        .queryParam("limit", "50")
                        .queryParam("offset", "10")
                        .queryParam("since", since.toString())
                        .build())
                .header("Authorization", "Bearer " + API_KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[0].url").isEqualTo("https://example.com/1.jpg")
                .jsonPath("$[0].hash").isEqualTo("hash-1")
                .jsonPath("$[0].mlDecision").isEqualTo("PUBLISH")
                .jsonPath("$[0].mlScore").isEqualTo(0.8)
                .jsonPath("$[0].mlReason").isEqualTo("reason");

        verify(historyService).exportTraining(50, 10, since);
    }

    @Test
    void shouldRejectWhenApiKeyIsMissing() {
        webTestClient.get()
                .uri("/api/vk-history/training/export")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(historyService);
    }

    @Test
    void shouldRejectWhenApiKeyIsInvalid() {
        webTestClient.get()
                .uri("/api/vk-history/training/export")
                .header("Authorization", "Bearer wrong")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(historyService);
    }
}
