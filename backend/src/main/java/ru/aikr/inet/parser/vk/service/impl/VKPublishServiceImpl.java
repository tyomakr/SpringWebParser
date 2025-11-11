package ru.aikr.inet.parser.vk.service.impl;

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
import ru.aikr.inet.parser.dto.VKPublishResult;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.vk.service.VKPublishService;
import ru.aikr.inet.parser.fishki.service.WebImageService;
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

    /** VK SDK client (один на всё приложение). */
    private final VkApiClient vk = new VkApiClient(HttpTransportClient.getInstance());

    private final WebImageService webImageService;

    /* ========== VK auth & config ========== */
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
    /* ====================================== */

    /**
     * Публикует список изображений во ВКонтакте.
     *
     * @param fullImagesList список модели WebImage (с прямыми ссылками и т.д.)
     * @return Mono с детальным результатом публикации
     */
    @Override
    public Mono<VKPublishResult> generatePostsAndPublishToCommunityWall(List<WebImage> fullImagesList) {
        UserActor actor = new UserActor(userId, accessToken);
        Path downloadDir = Path.of("downloaded");

        log.info(AnsiColors.CYAN + "=== Starting VK publish for {} images ===" + AnsiColors.RESET, 
                fullImagesList.size());

        // 1. Скачиваем изображения
        return webImageService.downloadImagesFromWebImageLinks(fullImagesList, downloadDir)
                .flatMapMany(paths -> {
                    if (paths.isEmpty()) {
                        log.warn(AnsiColors.YELLOW + "No images were downloaded" + AnsiColors.RESET);
                        return Flux.just(new ChunkResult(0, 0, 0, 0, "No images downloaded"));
                    }
                    
                    log.info(AnsiColors.CYAN + "Downloaded {} images, processing in chunks of {}" 
                            + AnsiColors.RESET, paths.size(), chunkSize);
                    
                    return Flux.fromIterable(paths)
                            // 2. разбиваем на чанки по chunkSize
                            .buffer(chunkSize)
                            // 3. для каждого чанка — обработка с ретраями
                            .concatMap(chunk ->
                                    Mono.fromCallable(() -> processChunkWithRetry(actor, chunk))
                                            .subscribeOn(Schedulers.boundedElastic())
                            );
                })
                // 4. собираем статистику
                .reduce(new ChunkResult(0, 0, 0, 0, null), 
                        (acc, result) -> new ChunkResult(
                                acc.uploaded + result.uploaded,
                                acc.published + result.published,
                                acc.postsPublished + result.postsPublished,
                                acc.postsFailed + result.postsFailed,
                                combineErrors(acc.error, result.error)
                        ))
                // 5. чистим временную папку и формируем итоговый результат
                .flatMap(result -> cleanUp(downloadDir).thenReturn(
                        new VKPublishResult(
                                result.uploaded,
                                result.published,
                                result.postsPublished,
                                result.postsFailed,
                                fullImagesList.size(),
                                result.error
                        )
                ))
                .doOnSuccess(result -> {
                    log.info(AnsiColors.CYAN + "=== VK PUBLISH COMPLETED: {} ===" + AnsiColors.RESET, result);
                    if (result.isPartialSuccess()) {
                        log.warn(AnsiColors.YELLOW + "Partial success: {} uploaded, {} published" 
                                + AnsiColors.RESET, result.getUploadedCount(), result.getPublishedCount());
                    }
                })
                .doOnError(error -> 
                        log.error(AnsiColors.RED + "=== VK PUBLISH FAILED: {} ===" + AnsiColors.RESET, 
                                error.getMessage())
                );
    }
    
    /**
     * Вспомогательный класс для агрегации результатов обработки чанков
     */
    private static class ChunkResult {
        final int uploaded;
        final int published;
        final int postsPublished;
        final int postsFailed;
        final String error;
        
        ChunkResult(int uploaded, int published, int postsPublished, int postsFailed, String error) {
            this.uploaded = uploaded;
            this.published = published;
            this.postsPublished = postsPublished;
            this.postsFailed = postsFailed;
            this.error = error;
        }
    }
    
    private String combineErrors(String error1, String error2) {
        if (error1 == null) return error2;
        if (error2 == null) return error1;
        return error1 + "; " + error2;
    }

    /**
     * Обработка одного чанка файлов: загрузка, удаление дубликатов, отправка поста.
     *
     * @param actor VK-актор
     * @param chunk список путей к файлам
     * @return результат обработки чанка с детальной статистикой
     */
    private ChunkResult processChunkWithRetry(UserActor actor, List<Path> chunk) {
        List<File> files = new ArrayList<>();
        for (Path p : chunk) {
            files.add(p.toFile());
        }

        // Убираем дубликаты, сохраняя порядок
        removeDuplicates(files);
        if (files.isEmpty()) {
            return new ChunkResult(0, 0, 0, 0, "Empty chunk after deduplication");
        }

        log.info(AnsiColors.CYAN + "Processing chunk of {} images" + AnsiColors.RESET, files.size());

        List<String> attachmentIds = new ArrayList<>();
        int uploadedCount = 0;
        String chunkError = null;

        // Загрузка с retry/backoff
        for (File file : files) {
            int attempt = 0;
            boolean uploaded = false;

            while (attempt < maxRetries && !uploaded) {
                attempt++;
                try {
                    String attach = uploadPhotoWithBackoff(actor, file);
                    attachmentIds.add(attach);
                    uploaded = true;
                    uploadedCount++;

                    // Пауза baseDelay после каждого успешного аплоада
                    TimeUnit.MILLISECONDS.sleep(baseDelay);
                } catch (ApiException e) {
                    handleApiError(e, file.getName(), attempt);
                    if (attempt < maxRetries) {
                        waitBeforeRetry(attempt);
                    } else {
                        String errorMsg = String.format("Failed to upload %s after %d attempts: %s", 
                                file.getName(), maxRetries, getErrorMessage(e));
                        log.error(AnsiColors.RED + errorMsg + AnsiColors.RESET);
                        chunkError = combineErrors(chunkError, errorMsg);
                    }
                } catch (Exception e) {
                    log.error(AnsiColors.RED + "Critical error uploading {} [{}]: {}" + AnsiColors.RESET,
                            file.getName(), e.getClass().getSimpleName(), e.getMessage());
                    if (attempt < maxRetries) {
                        waitBeforeRetry(attempt);
                    } else {
                        String errorMsg = String.format("Critical error uploading %s: %s", 
                                file.getName(), e.getMessage());
                        chunkError = combineErrors(chunkError, errorMsg);
                    }
                }
            }
        }

        // Публикация поста, если хоть одно изображение загрузилось
        int publishedCount = 0;
        int postsPublished = 0;
        int postsFailed = 0;
        
        if (!attachmentIds.isEmpty()) {
            try {
                publishPost(actor, attachmentIds);
                publishedCount = attachmentIds.size();
                postsPublished = 1;
                log.info(AnsiColors.GREEN + "Successfully published chunk with {} images" 
                        + AnsiColors.RESET, attachmentIds.size());
            } catch (ApiException e) {
                handleApiError(e, "chunk post", 1);
                String errorMsg = String.format("Failed to publish post with %d images: %s", 
                        attachmentIds.size(), getErrorMessage(e));
                log.error(AnsiColors.RED + errorMsg + AnsiColors.RESET);
                chunkError = combineErrors(chunkError, errorMsg);
                postsFailed = 1;
                // НЕ обнуляем uploadedCount - изображения загружены, просто пост не опубликован
            } catch (Exception e) {
                String errorMsg = String.format("Critical error publishing post: %s", e.getMessage());
                log.error(AnsiColors.RED + errorMsg + AnsiColors.RESET);
                chunkError = combineErrors(chunkError, errorMsg);
                postsFailed = 1;
                // НЕ обнуляем uploadedCount
            }
        }

        return new ChunkResult(uploadedCount, publishedCount, postsPublished, postsFailed, chunkError);
    }

    /* ======== VK API Helpers ======== */

    private String uploadPhotoWithBackoff(UserActor actor, File file)
            throws ApiException, ClientException {

        GetWallUploadServerResponse server =
                vk.photos().getWallUploadServer(actor).execute();

        WallUploadResponse upload = vk.upload()
                .photoWall(String.valueOf(server.getUploadUrl()), file)
                .execute();

        SaveWallPhotoResponse photo = vk.photos()
                .saveWallPhoto(actor, upload.getPhoto())
                .server(upload.getServer())
                .hash(upload.getHash())
                .execute()
                .getFirst();

        log.info(AnsiColors.GREEN + "Uploaded {} → photo{}_{}" + AnsiColors.RESET,
                file.getName(), photo.getOwnerId(), photo.getId()
        );

        return "photo" + photo.getOwnerId() + "_" + photo.getId();
    }

    private void publishPost(UserActor actor, List<String> attachmentIds)
            throws ClientException, ApiException {

        String attachments = String.join(",", attachmentIds);
        PostResponse resp = vk.wall().post(actor)
                .ownerId(groupId)
                .attachments(attachments)
                .execute();

        log.info(AnsiColors.GREEN + "Published post ID {} | Images {}" + AnsiColors.RESET,
                resp.getPostId(), attachmentIds.size()
        );
    }

    /* ======== Utility Methods ======== */

    /**
     * Обрабатывает ошибки VK API с детализацией по типам
     */
    private void handleApiError(ApiException e, String context, int attempt) {
        String codeDescription = getVKErrorDescription(e.getCode());
        log.warn(AnsiColors.YELLOW + "VK API error [code={}] {}: {} (attempt {})" + AnsiColors.RESET,
                e.getCode(), codeDescription, e.getMessage(), attempt);
    }
    
    /**
     * Получает описание кода ошибки VK API
     */
    private String getVKErrorDescription(int code) {
        return switch (code) {
            case 1 -> "Unknown error";
            case 6 -> "Too many requests per second (rate limit)";
            case 9 -> "Flood control";
            case 10 -> "Internal server error";
            case 14 -> "Captcha needed";
            case 15 -> "Access denied";
            case 18 -> "User was deleted or banned";
            case 100 -> "One of the parameters is missing or invalid";
            case 113 -> "Invalid user id";
            case 121 -> "Invalid album id";
            case 122 -> "Invalid server";
            case 125 -> "Invalid photos list";
            case 126 -> "Invalid hash";
            case 200 -> "Access denied to perform this action";
            default -> "Error code " + code;
        };
    }
    
    /**
     * Формирует понятное сообщение об ошибке
     */
    private String getErrorMessage(ApiException e) {
        return String.format("[%s] %s", getVKErrorDescription(e.getCode()), e.getMessage());
    }

    /**
     * Экспоненциальная задержка перед повтором ―
     * основана на baseDelay, но не превышает maxDelay.
     */
    private void waitBeforeRetry(int attempt) {
        int delay = Math.min(
                (int) (baseDelay * Math.pow(2, attempt)),
                maxDelay
        );
        try {
            log.info(AnsiColors.CYAN + "Waiting {} ms before retry" + AnsiColors.RESET, delay);
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Удаляет дубликаты из списка, сохраняя порядок
     */
    private static <T> void removeDuplicates(List<T> list) {
        Set<T> unique = new LinkedHashSet<>(list);
        list.clear();
        list.addAll(unique);
    }

    private Mono<Void> cleanUp(Path dir) {
        return Mono.fromRunnable(() -> {
                    try (var stream = Files.list(dir)) {
                        stream.forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
                    } catch (Exception ignored) {
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
