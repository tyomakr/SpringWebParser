package ru.aikr.inet.parser.mlfeedback.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRequestItem;
import ru.aikr.inet.parser.mlfeedback.service.MlFeedbackService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = MlFeedbackController.class)
@Import(MlFeedbackControllerTest.TestConfig.class)
class MlFeedbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MlFeedbackService feedbackService;

    @Test
    void postFeedbackReturnsSavedCount() {
        when(feedbackService.saveFeedback(anyList())).thenReturn(4);

        MlFeedbackRequestItem item = new MlFeedbackRequestItem(1L, "https://example.com", "hash",
                "PUBLISH", 0.5, "reason", "hit");

        webTestClient.post()
                .uri("/api/ml/feedback")
                .bodyValue(List.of(item))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.saved").isEqualTo(4);
    }

    @Test
    void postFeedbackRejectsInvalidDecision() {
        MlFeedbackRequestItem item = new MlFeedbackRequestItem(1L, "https://example.com", "hash",
                "WRONG", null, null, null);

        webTestClient.post()
                .uri("/api/ml/feedback")
                .bodyValue(List.of(item))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getFeedbackReturnsSavedRecords() {
        MlFeedbackRecord record = new MlFeedbackRecord();
        record.setId(1L);
        record.setCandidateId(1L);
        record.setUrl("https://example.com");
        record.setHash("hash");
        record.setDecision("PUBLISH");
        record.setScore(0.6);
        record.setReason("reason");
        record.setZone("gray");

        when(feedbackService.fetchFeedback(50, 0, null)).thenReturn(List.of(record));

        webTestClient.get()
                .uri("/api/ml/feedback")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[0].decision").isEqualTo("PUBLISH")
                .jsonPath("$[0].zone").isEqualTo("gray");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MlFeedbackService feedbackService() {
            return Mockito.mock(MlFeedbackService.class);
        }
    }
}
