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
import ru.aikr.inet.parser.history.model.VkHistoryStats;
import ru.aikr.inet.parser.history.model.VkHistoryTrainingToggleRequest;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.history.service.VkWallSyncScheduler;
import ru.aikr.inet.parser.history.service.VkWallSyncService;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.time.Instant;
import java.util.List;

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

    @Test
    void entriesIncludeMlContext() {
        VkImageHistoryRecord record = new VkImageHistoryRecord(
                1L,
                "https://example.com/a.jpg",
                "hash-a",
                Instant.now()
        );
        record.setId(1L);
        record.setMlDecision("PUBLISH");
        record.setMlScore(0.85);
        record.setMlReason("best");
        record.setUseForTraining(true);

        when(historyService.getHistoryEntries()).thenReturn(List.of(record));

        webTestClient.get()
                .uri("/api/vk-history/entries")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].mlDecision").isEqualTo("PUBLISH")
                .jsonPath("$[0].mlScore").isEqualTo(0.85)
                .jsonPath("$[0].mlReason").isEqualTo("best");
    }

    @Test
    void trainingReturnsOnlyActiveEntries() {
        VkImageHistoryRecord allowed = new VkImageHistoryRecord(
                1L,
                "https://example.com/a.jpg",
                "hash-a",
                Instant.now()
        );
        allowed.setId(1L);
        allowed.setUseForTraining(true);
        VkImageHistoryRecord denied = new VkImageHistoryRecord(
                2L,
                "https://example.com/b.jpg",
                "hash-b",
                Instant.now()
        );
        denied.setId(2L);
        denied.setUseForTraining(false);

        when(historyService.getTrainingEntries()).thenReturn(List.of(allowed));

        webTestClient.get()
                .uri("/api/vk-history/training")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].useForTraining").isEqualTo(true)
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void updateTrainingFlagDelegatesToService() {
        webTestClient.patch()
                .uri("/api/vk-history/entries/5/training")
                .bodyValue(new VkHistoryTrainingToggleRequest() {{
                    setUseForTraining(false);
                }})
                .exchange()
                .expectStatus().isOk();

        Mockito.verify(historyService).updateUseForTraining(5L, false);
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
            // тоже мок, чтобы удовлетворить SpringWebParserApplication
            return Mockito.mock(LogEventsPublisher.class);
        }
    }
}
