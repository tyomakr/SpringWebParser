package ru.aikr.inet.parser.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.dto.VKPublishResult;
import ru.aikr.inet.parser.logging.LogEventsPublisher;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(FishkiRestController.class)
class FishkiRestControllerTest {

    @Autowired
    WebTestClient web;

    @MockitoBean
    WebImageService webImageService;

    @MockitoBean
    VKPublishService vkPublishService;

    @MockitoBean
    LogEventsPublisher logEventsPublisher;

    @Test
    void shouldReturnImagesFromPages() {
        when(webImageService.getImagesFromPages(1, 5))
                .thenReturn(Flux.just(new WebImage("test_url")));

        web.get().uri("/api/v1/sites/fishki/images/1/to/5")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(WebImage.class).hasSize(1)
                .value(list ->
                        // проверяем первый элемент
                        list.getFirst().getDirectLink().equals("test_url")
                );
    }

    @Test
    void shouldPublishImages() {
        // сервис теперь отдаёт VKPublishResult с детальной статистикой
        VKPublishResult result = new VKPublishResult(
                1,  // uploadedCount
                1,  // publishedCount
                1,  // postsPublished
                0,  // postsFailed
                1,  // totalProcessed
                null // errorMessage
        );
        when(vkPublishService.generatePostsAndPublishToCommunityWall(any()))
                .thenReturn(Mono.just(result));

        web.post().uri("/api/v1/sites/fishki/images/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(new WebImage("https://example.com/test.jpg")))   // JSON сериализуется автоматически
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(message -> message.contains("Успешно опубликовано 1 изображений"));
    }
}