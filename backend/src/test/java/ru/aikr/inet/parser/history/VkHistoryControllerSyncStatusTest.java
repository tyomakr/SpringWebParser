package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.aikr.inet.parser.history.controller.VkHistoryController;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.model.VkWallSyncStatus;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.history.service.VkWallSyncScheduler;
import ru.aikr.inet.parser.history.service.VkWallSyncService;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.time.Instant;

@WebFluxTest(controllers = VkHistoryController.class)
@Import(VkHistoryControllerSyncStatusTest.TestConfig.class)
class VkHistoryControllerSyncStatusTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private VkWallSyncScheduler scheduler;

    @Test
    void statusReturnsLastRun() {
        VkWallSyncStatus status = new VkWallSyncStatus(
                false,
                Instant.parse("2024-01-01T00:00:00Z"),
                new VkWallSyncReport(1, 2, 1, 1),
                null,
                Instant.parse("2024-01-01T00:01:00Z"),
                Instant.parse("2024-01-01T00:00:00Z"),
                null
        );
        Mockito.when(scheduler.getStatus()).thenReturn(status);

        webTestClient.get()
                .uri("/api/vk-history/sync-wall/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.running").isEqualTo(false)
                .jsonPath("$.lastReport.inserted").isEqualTo(1);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        VkHistoryService historyService() {
            return Mockito.mock(VkHistoryService.class);
        }

        @Bean
        VkWallSyncScheduler wallSyncScheduler() {
            return Mockito.mock(VkWallSyncScheduler.class);
        }

        @Bean
        VkWallSyncService wallSyncService() {
            return Mockito.mock(VkWallSyncService.class);
        }

        @Bean
        LogEventsPublisher logEventsPublisher() {
            return Mockito.mock(LogEventsPublisher.class);
        }
    }
}
