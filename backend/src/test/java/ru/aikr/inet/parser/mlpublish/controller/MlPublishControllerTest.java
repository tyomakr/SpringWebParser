package ru.aikr.inet.parser.mlpublish.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.dto.VKPublishResult;
import ru.aikr.inet.parser.mlpublish.client.MlRecommendationClient;
import ru.aikr.inet.parser.mlpublish.controller.MlPublishController;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationException;
import ru.aikr.inet.parser.mlpublish.model.MlPublishCandidate;
import ru.aikr.inet.parser.mlpublish.model.MlPublishCommitItem;
import ru.aikr.inet.parser.mlpublish.model.MlPublishCommitRequest;
import ru.aikr.inet.parser.mlpublish.model.MlPublishCommitResponse;
import ru.aikr.inet.parser.mlpublish.model.MlPublishPreviewRequest;
import ru.aikr.inet.parser.mlpublish.model.MlPublishPreviewResponse;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendation;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.logging.LogEventsPublisher;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;
import ru.aikr.inet.parser.service.VKPublishService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@WebFluxTest(MlPublishController.class)
class MlPublishControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    MlRecommendationClient mlRecommendationClient;

    @MockitoBean
    VKPublishService vkPublishService;

    @MockitoBean
    VkHistoryService vkHistoryService;

    @MockitoBean
    LogEventsPublisher logEventsPublisher;

    @Test
    void previewReturnsRecommendations() {
        MlRecommendation result = new MlRecommendation(
                "1",
                "https://example.com/1.jpg",
                0.8,
                "good",
                RecommendationDecision.PUBLISH
        );

        when(mlRecommendationClient.recommend(anyList()))
                .thenReturn(Mono.just(List.of(result)));

        MlPublishPreviewRequest request = new MlPublishPreviewRequest(List.of(
                new MlPublishCandidate("1", "https://example.com/1.jpg")
        ));

        webTestClient.post().uri("/api/ml/publish/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recommendations[0].id").isEqualTo("1")
                .jsonPath("$.recommendations[0].recommendation").isEqualTo("PUBLISH");
    }

    @Test
    void previewReturnsOrderedByScore() {
        MlRecommendation first = new MlRecommendation("1", "https://example.com/1.jpg", 0.5,
                "ok", RecommendationDecision.REVIEW);
        MlRecommendation second = new MlRecommendation("2", "https://example.com/2.jpg", 0.9,
                "best", RecommendationDecision.PUBLISH);
        MlRecommendation third = new MlRecommendation("3", "https://example.com/3.jpg", 0.3,
                "skip", RecommendationDecision.SKIP);

        when(mlRecommendationClient.recommend(anyList()))
                .thenReturn(Mono.just(List.of(first, second, third)));

        MlPublishPreviewRequest request = new MlPublishPreviewRequest(List.of(
                new MlPublishCandidate("1", "https://example.com/1.jpg")
        ));

        webTestClient.post().uri("/api/ml/publish/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recommendations[0].id").isEqualTo("2")
                .jsonPath("$.recommendations[1].id").isEqualTo("1")
                .jsonPath("$.recommendations[2].id").isEqualTo("3");
    }

    @Test
    void previewHandlesMlErrorsGracefully() {
        when(mlRecommendationClient.recommend(anyList()))
                .thenReturn(Mono.error(new MlRecommendationException("ml down")));

        MlPublishPreviewRequest request = new MlPublishPreviewRequest(List.of(
                new MlPublishCandidate("1", "https://example.com/1.jpg")
        ));

        webTestClient.post().uri("/api/ml/publish/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.recommendations.length()").isEqualTo(0);
    }

    @Test
    void previewEmitsLogEventWithSummary() {
        MlRecommendation publish = new MlRecommendation("1", "https://example.com/1.jpg", 0.7,
                "best", RecommendationDecision.PUBLISH);
        MlRecommendation review = new MlRecommendation("2", "https://example.com/2.jpg", 0.5,
                "maybe", RecommendationDecision.REVIEW);
        MlRecommendation skip = new MlRecommendation("3", "https://example.com/3.jpg", 0.3,
                "bad", RecommendationDecision.SKIP);

        when(mlRecommendationClient.recommend(anyList()))
                .thenReturn(Mono.just(List.of(publish, review, skip)));

        MlPublishPreviewRequest request = new MlPublishPreviewRequest(List.of(
                new MlPublishCandidate("1", "https://example.com/1.jpg")
        ));

        webTestClient.post().uri("/api/ml/publish/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logEventsPublisher).publish(captor.capture());
        String payload = captor.getValue();
        assertTrue(payload.contains("\"event\":\"ml-preview\""));
        assertTrue(payload.contains("\"total\":3"));
        assertTrue(payload.contains("\"publish\":1"));
        assertTrue(payload.contains("\"review\":1"));
        assertTrue(payload.contains("\"skip\":1"));
        assertTrue(payload.contains("\"maxScore\":0.700"));
    }

    @Test
    void commitPublishesSelectedImages() {
        when(vkPublishService.generatePostsAndPublishToCommunityWall(anyList()))
                .thenReturn(Mono.just(new VKPublishResult(
                        1,
                        1,
                        1,
                        0,
                        1,
                        null
                )));

        MlPublishCommitRequest request = new MlPublishCommitRequest(List.of(
                new MlPublishCommitItem("1", "https://example.com/1.jpg", true,
                        RecommendationDecision.PUBLISH, 0.7, "best"),
                new MlPublishCommitItem("2", "https://example.com/2.jpg", false,
                        null, null, null)
        ));

        webTestClient.post().uri("/api/ml/publish/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.uploadedCount").isEqualTo(1)
                .jsonPath("$.publishedCount").isEqualTo(1);

        ArgumentCaptor<VkImageHistoryRecord> recordCaptor = ArgumentCaptor.forClass(VkImageHistoryRecord.class);
        ArgumentCaptor<String> decisionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);

        verify(vkHistoryService).recordPublication(
                recordCaptor.capture(),
                decisionCaptor.capture(),
                scoreCaptor.capture(),
                reasonCaptor.capture()
        );

        assertEquals("PUBLISH", decisionCaptor.getValue());
        assertEquals(0.7, scoreCaptor.getValue());
        assertEquals("best", reasonCaptor.getValue());
    }
}
