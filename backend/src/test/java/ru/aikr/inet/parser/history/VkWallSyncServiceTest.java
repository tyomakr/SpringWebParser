package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.history.service.VkWallSyncService;
import ru.aikr.inet.parser.vk.VkApiClient;
import ru.aikr.inet.parser.vk.VkApiProperties;
import ru.aikr.inet.parser.vk.dto.WallGetResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VkWallSyncServiceTest {

    private static final long GROUP_ID = -1L;
    private static final String DUPLICATE_URL = "https://vk/photo-duplicate";

    @Mock
    private VkApiClient vkApiClient;

    @Mock
    private VkHistoryRepository repository;

    private VkWallSyncService service;

    @BeforeEach
    void setUp() {
        VkApiProperties properties = new VkApiProperties();
        properties.setGroupId(GROUP_ID);
        service = new VkWallSyncService(vkApiClient, repository, properties, Duration.ZERO);
    }

    @Test
    void syncWallProcessesMultiplePagesAndSkipsDuplicates() {
        WallGetResponse firstPage = response(post(1001L, 1_500L, "https://vk/photo-1", "https://vk/photo-2"));
        WallGetResponse secondPage = response(post(1002L, 1_400L, DUPLICATE_URL));

        when(vkApiClient.wallGet(GROUP_ID, 100, 0)).thenReturn(Mono.just(firstPage));
        when(vkApiClient.wallGet(GROUP_ID, 100, 100)).thenReturn(Mono.just(secondPage));
        when(repository.saveIfAbsent(ArgumentMatchers.any())).thenReturn(true);
        when(repository.saveIfAbsent(ArgumentMatchers.argThat(record -> DUPLICATE_URL.equals(record.getUrl()))))
                .thenReturn(false);

        VkWallSyncReport report = service.syncWall(null, 2);

        assertThat(report.postsFetched()).isEqualTo(2);
        assertThat(report.photosFound()).isEqualTo(3);
        assertThat(report.inserted()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(1);
    }

    @Test
    void syncWallStopsWhenSinceTrimsOldPosts() {
        WallGetResponse page = response(
                post(2001L, 1_200L, "https://vk/photo-new"),
                post(2002L, 800L, "https://vk/photo-old")
        );
        when(vkApiClient.wallGet(GROUP_ID, 100, 0)).thenReturn(Mono.just(page));
        when(repository.saveIfAbsent(ArgumentMatchers.any())).thenReturn(true);

        Instant since = Instant.ofEpochSecond(1_000L);
        VkWallSyncReport report = service.syncWall(since, 3);

        assertThat(report.postsFetched()).isEqualTo(1);
        assertThat(report.photosFound()).isEqualTo(1);
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(0);

        verify(vkApiClient, times(1)).wallGet(GROUP_ID, 100, 0);
        verify(repository, times(1)).saveIfAbsent(ArgumentMatchers.any());
    }

    private static WallGetResponse response(WallGetResponse.WallPost... posts) {
        WallGetResponse response = new WallGetResponse();
        WallGetResponse.Response payload = new WallGetResponse.Response();
        payload.setItems(Arrays.asList(posts));
        response.setResponse(payload);
        return response;
    }

    private static WallGetResponse.WallPost post(long id, long date, String... urls) {
        WallGetResponse.WallPost post = new WallGetResponse.WallPost();
        post.setId(id);
        post.setDate(date);
        if (urls != null && urls.length > 0) {
            List<WallGetResponse.Attachment> attachments = Arrays.stream(urls)
                    .map(VkWallSyncServiceTest::photoAttachment)
                    .toList();
            post.setAttachments(attachments);
        }
        return post;
    }

    private static WallGetResponse.Attachment photoAttachment(String url) {
        WallGetResponse.PhotoSize size = new WallGetResponse.PhotoSize();
        size.setUrl(url);
        size.setWidth(600);
        size.setHeight(400);
        WallGetResponse.Photo photo = new WallGetResponse.Photo();
        photo.setSizes(List.of(size));
        WallGetResponse.Attachment attachment = new WallGetResponse.Attachment();
        attachment.setType("photo");
        attachment.setPhoto(photo);
        return attachment;
    }
}
