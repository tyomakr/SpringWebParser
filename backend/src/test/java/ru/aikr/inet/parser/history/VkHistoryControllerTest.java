package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import ru.aikr.inet.parser.logging.LogEventsPublisher;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = VkHistoryController.class)
@Import(VkHistoryControllerTest.TestConfig.class)
class VkHistoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private VkHistoryService historyService;

    @Test
    void refreshReturnsStats() {
        VkHistoryStats stats = new VkHistoryStats(5, 2, Instant.now());
        when(historyService.refreshFromVk()).thenReturn(stats);

        webTestClient.post()
                .uri("/api/vk-history/refresh")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalCount").isEqualTo(5)
                .jsonPath("$.updatedCount").isEqualTo(2);
    }

    @Test
    void statsReturnsCurrentStats() {
        VkHistoryStats stats = new VkHistoryStats(10, 0, Instant.now());
        when(historyService.currentStats()).thenReturn(stats);

        webTestClient.get()
                .uri("/api/vk-history/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalCount").isEqualTo(10);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        VkHistoryService historyService() {
            return Mockito.mock(VkHistoryService.class);
        }

        @Bean
        LogEventsPublisher logEventsPublisher() {
            // тоже мок, чтобы удовлетворить SpringWebParserApplication
            return Mockito.mock(LogEventsPublisher.class);
        }
    }
}