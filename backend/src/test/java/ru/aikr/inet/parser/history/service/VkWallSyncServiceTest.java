package ru.aikr.inet.parser.history.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.history.model.VkSyncProperties;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.vk.VkApiClient;
import ru.aikr.inet.parser.vk.VkApiProperties;
import ru.aikr.inet.parser.vk.dto.WallGetResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class VkWallSyncServiceTest {

    @Mock
    private VkApiClient vkApiClient;
    @Mock
    private VkHistoryRepository repository;

    private VkWallSyncService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        VkApiProperties properties = new VkApiProperties();
        properties.setGroupId(-1L);
        VkSyncProperties syncProperties = new VkSyncProperties();
        service = new VkWallSyncService(vkApiClient, repository, properties, syncProperties, new SimpleMeterRegistry());
    }

    @Test
    void rateLimitThrows() {
        HttpHeaders headers = new HttpHeaders();
        byte[] payload = "{\"error_code\":6}".getBytes(StandardCharsets.UTF_8);
        WebClientResponseException ex = WebClientResponseException.create(
                200,
                "OK",
                headers,
                Objects.requireNonNull(payload),
                StandardCharsets.UTF_8);
        when(vkApiClient.wallGet(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(Mono.error(ex));

        assertThatThrownBy(() -> service.syncWall(null, 1))
                .isInstanceOf(VkWallSyncService.RateLimitException.class);
    }

    @Test
    void stopsWhenSinceCoversAll() {
        WallGetResponse response = new WallGetResponse();
        WallGetResponse.Response body = new WallGetResponse.Response();
        WallGetResponse.WallPost post = new WallGetResponse.WallPost();
        post.setDate(Instant.now().minusSeconds(3600).getEpochSecond());
        WallGetResponse.Attachment attachment = new WallGetResponse.Attachment();
        attachment.setType("photo");
        WallGetResponse.Photo photo = new WallGetResponse.Photo();
        WallGetResponse.PhotoSize size = new WallGetResponse.PhotoSize();
        size.setUrl("http://example.com/a.jpg");
        size.setWidth(100);
        size.setHeight(100);
        photo.setSizes(List.of(size));
        attachment.setPhoto(photo);
        post.setAttachments(List.of(attachment));
        body.setItems(List.of(post));
        response.setResponse(body);
        when(vkApiClient.wallGet(ArgumentMatchers.anyLong(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(Mono.just(response));
        when(repository.saveIfAbsent(ArgumentMatchers.any())).thenReturn(false);

        Instant since = Instant.now();
        VkWallSyncReport report = service.syncWall(since, 1);
        assertThat(report.inserted()).isZero();
    }

}
