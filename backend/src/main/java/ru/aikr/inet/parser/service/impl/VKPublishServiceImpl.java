package ru.aikr.inet.parser.service.impl;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

    // VK SDK client
    private final VkApiClient vk = new VkApiClient(HttpTransportClient.getInstance());
    private final WebImageService webImageService;

    @Value("${vk.user-id}")
    private Long userId;
    @Value("${vk.group-id}")
    private Long groupId;
    @Value("${vk.access-token}")
    private String accessToken;
    @Value("${env.vk-publisher.chunk-size}")
    private Integer chunkSize;
    @Value("${env.vk-publisher.max-retries}")
    private Integer maxRetries;
    @Value("${env.vk-publisher.delay-between-retries-ms}")
    private Integer baseDelay;
    @Value("${env.vk-publisher.max-delay-ms}")
    private Integer maxDelay;

    @Override
    public Mono<Boolean> generatePostsAndPublishToCommunityWall(List<WebImage> fullImagesList) {
        UserActor actor = new UserActor(userId, accessToken);

        // Шаг 1–2: скачиваем изображения в папку "downloaded"
        Path downloadDir = Path.of("downloaded");
        return webImageService
                .downloadImagesFromWebImageLinks(fullImagesList, downloadDir)    // Mono<List<Path>>
                .flatMapMany(paths -> Flux.fromIterable(paths)                   // Flux<Path>
                        .buffer(chunkSize)                                          // разбиваем на чанки
                        .concatMap(chunk ->
                                // Для каждого чанка выполняем всю синхронную логику в boundedElastic
                                Mono.fromCallable(() -> processChunkWithRetry(actor, chunk))
                                        .subscribeOn(Schedulers.boundedElastic())
                        )
                )
                .all(Boolean::booleanValue)                                      // true, если все чанки успешны
                .flatMap(allSuccess ->
                        // После завершения публикации — чистим загруженные файлы
                        webImageService.downloadImagesFromWebImageLinks(fullImagesList, downloadDir)
                                .flatMap(paths -> Mono.fromRunnable(() -> {
                                    for (Path p : paths) {
                                        try { Files.deleteIfExists(p); }
                                        catch (Exception ignored) {}
                                    }
                                })
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .thenReturn(allSuccess))
                )
                .doOnTerminate(() -> log.info(AnsiColors.CYAN + "=== VK PUBLISH COMPLETED ===" + AnsiColors.RESET));
    }

    /** возвращает true, если chunk успешно обработан. */
    private boolean processChunkWithRetry(UserActor actor, List<Path> chunk) {
        // Конвертим Path → File
        List<File> files = new ArrayList<>();
        for (Path p : chunk) files.add(p.toFile());

        //удаляем дубликаты
        log.info(AnsiColors.CYAN + "Checking duplicates..." + AnsiColors.RESET);
        int initialSize = files.size();
        removeDuplicates(files);
        log.info(AnsiColors.CYAN + "{}" + AnsiColors.RESET, String.format("Removed %d duplicates", initialSize - files.size()));

        //процессинг текущего чанка
        log.info(AnsiColors.CYAN + "{}" + AnsiColors.RESET, String.format("Processing chunk of %d images", files.size()));

        List<String> attachmentIds = new ArrayList<>();
        boolean hasErrors = false;

        for (File file : files) {
            int attempt = 0;
            boolean fileSuccess = false;
            while (attempt < maxRetries && !fileSuccess) {
                attempt++;
                try {
                    String attachment = uploadPhotoWithBackoff(actor, file);
                    attachmentIds.add(attachment);
                    fileSuccess = true;
                    TimeUnit.MILLISECONDS.sleep(baseDelay);
                } catch (ApiException e) {
                    handleApiError(e);
                    waitBeforeRetry(attempt);
                } catch (Exception e) {
                    log.error(AnsiColors.RED + "Critical error: {}" + AnsiColors.RESET, e.getMessage());
                }
            }
            if (!fileSuccess) {
                hasErrors = true;
                log.error(AnsiColors.RED + "{}" + AnsiColors.RESET, String.format(
                        "File %s failed after %d attempts", file.getName(), maxRetries
                ));
            }
        }

        if (!attachmentIds.isEmpty()) {
            try {
                publishPost(actor, attachmentIds);
                return !hasErrors;
            } catch (Exception e) {
                log.error(AnsiColors.RED + "Publish error: {}" + AnsiColors.RESET, e.getMessage());
                return false;
            }
        }
        return false;
    }

    private String uploadPhotoWithBackoff(UserActor actor, File file)
            throws ApiException, ClientException {
        // Получаем URL для загрузки
        GetWallUploadServerResponse server = vk.photos().getWallUploadServer(actor).execute();
        WallUploadResponse upload = vk.upload()
                .photoWall(String.valueOf(server.getUploadUrl()), file)
                .execute();
        // Сохраняем фото
        SaveWallPhotoResponse photo = vk.photos()
                .saveWallPhoto(actor, upload.getPhoto())
                .server(upload.getServer())
                .hash(upload.getHash())
                .execute()
                .getFirst();
        log.info(AnsiColors.GREEN + "{}" + AnsiColors.RESET, String.format(
                "Uploaded %s → photo%d_%d", file.getName(), photo.getOwnerId(), photo.getId()
        ));
        return "photo" + photo.getOwnerId() + "_" + photo.getId();
    }

    private void publishPost(UserActor actor, List<String> attachmentIds)
            throws ClientException, ApiException {
        String attachments = String.join(",", attachmentIds);
        PostResponse response = vk.wall().post(actor)
                .ownerId(groupId)
                .attachments(attachments)
                .execute();
        log.info(AnsiColors.GREEN + "{}" + AnsiColors.RESET, String.format(
                "Published post ID: %d | Images: %d",
                response.getPostId(), attachmentIds.size()
        ));
    }

    private void handleApiError(ApiException e) {
        if (e.getCode() == 6) {
            log.warn(AnsiColors.YELLOW + "VK rate limit: {}" + AnsiColors.RESET, e.getMessage());
        } else {
            log.warn(AnsiColors.YELLOW + "{}" + AnsiColors.RESET, e.getMessage());
        }
    }

    private void waitBeforeRetry(int attempt) {
        int delay = Math.min((int)(baseDelay * Math.pow(2, attempt)), maxDelay);
        try {
            log.info(AnsiColors.CYAN + "Waiting {}ms before retry" + AnsiColors.RESET, delay);
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException ignored) {}
    }

    private static <T> void removeDuplicates(List<T> list) {
        Set<T> unique = new LinkedHashSet<>(list);
        list.clear();
        list.addAll(unique);
    }
}
