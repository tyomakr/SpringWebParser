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

@WebFluxTest(controllers = VkHistoryController.class)
@Import(VkHistoryControllerSyncWallTest.TestConfig.class)
class VkHistoryControllerSyncWallTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private VkWallSyncService wallSyncService;

    @Autowired
    private VkWallSyncScheduler wallSyncScheduler;

    @Test
    void syncWallReturnsReport() {
        VkWallSyncReport report = new VkWallSyncReport(2, 3, 2, 1);
        Mockito.when(wallSyncService.syncWall(null, 0)).thenReturn(report);

        webTestClient.post()
                .uri("/api/vk-history/sync-wall")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.postsFetched").isEqualTo(2)
                .jsonPath("$.photosFound").isEqualTo(3)
                .jsonPath("$.inserted").isEqualTo(2)
                .jsonPath("$.skipped").isEqualTo(1);
    }

    @Test
    void syncWallTriggerStartsBackgroundJob() {
        VkWallSyncStatus status = new VkWallSyncStatus(false, null, null, null, null, null, null);
        Mockito.when(wallSyncScheduler.triggerManualSync(null, 0)).thenReturn(true);
        Mockito.when(wallSyncScheduler.getStatus()).thenReturn(status);

        webTestClient.post()
                .uri("/api/vk-history/sync-wall/trigger")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.running").isEqualTo(false);
    }

    @Test
    void syncWallTriggerRejectsWhenRunning() {
        Mockito.when(wallSyncScheduler.triggerManualSync(null, 0)).thenReturn(false);

        webTestClient.post()
                .uri("/api/vk-history/sync-wall/trigger")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void syncWallRejectsInvalidSince() {
        webTestClient.post()
                .uri("/api/vk-history/sync-wall?since=not-a-date")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        VkHistoryService historyService() {
            return Mockito.mock(VkHistoryService.class);
        }

        @Bean
        VkWallSyncService wallSyncService() {
            return Mockito.mock(VkWallSyncService.class);
        }

        @Bean
        VkWallSyncScheduler wallSyncScheduler() {
            return Mockito.mock(VkWallSyncScheduler.class);
        }

        @Bean
        LogEventsPublisher logEventsPublisher() {
            return Mockito.mock(LogEventsPublisher.class);
        }
    }
}
