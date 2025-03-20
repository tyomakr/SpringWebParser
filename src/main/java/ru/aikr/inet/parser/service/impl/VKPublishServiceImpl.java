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
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

    private static final Logger log = Logger.getLogger("VKPublishService");

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
    public boolean generatePostsAndPublishToCommunityWall(List<WebImage> fullImagesList) {

        log.info(AnsiColors.CYAN + "\n=== VK PUBLISHING STARTED ===" + AnsiColors.RESET);
        boolean isAllSuccess = true;
        UserActor userActor = new UserActor(userId, accessToken);
        List<File> fileList = null;

        try {
            // Этап 1: Удаление дубликатов
            log.info(AnsiColors.CYAN + "Checking duplicates..." + AnsiColors.RESET);
            int initialSize = fullImagesList.size();
            removeDuplicates(fullImagesList);
            log.info(AnsiColors.CYAN + String.format(
                    "Removed %d duplicates", initialSize - fullImagesList.size()
            ) + AnsiColors.RESET);

            // Этап 2: Загрузка изображений
            log.info(AnsiColors.CYAN + "Downloading images..." + AnsiColors.RESET);
            fileList = webImageService.downloadImagesFromWebImageLinks(fullImagesList);
            log.info(AnsiColors.CYAN + String.format(
                    "Downloaded %d files", fileList.size()
            ) + AnsiColors.RESET);

            // Этап 3: Разделение на чанки
            log.info(AnsiColors.CYAN + "Splitting into chunks..." + AnsiColors.RESET);
            List<List<File>> chunks = chunkify(fileList, chunkSize);
            log.info(AnsiColors.CYAN + String.format(
                    "Created %d chunks (%d files each)", chunks.size(), chunkSize
            ) + AnsiColors.RESET);

            // Этап 4: Публикация
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                List<File> chunk = chunks.get(chunkIndex);
                log.info(AnsiColors.CYAN + String.format(
                        "Processing chunk %d/%d",
                        chunkIndex + 1, chunks.size()
                ) + AnsiColors.RESET);

                boolean chunkSuccess = processChunkWithRetry(userActor, chunk, chunkIndex + 1);
                if (!chunkSuccess) {
                    isAllSuccess = false;
                    log.severe(AnsiColors.RED + String.format(
                            "Chunk %d failed after %d attempts", chunkIndex + 1, maxRetries
                    ) + AnsiColors.RESET);
                }
            }

        } finally {
            log.info(AnsiColors.CYAN + "Cleaning temporary files..." + AnsiColors.RESET);
            if (fileList != null) {
                deleteDownloadedFiles(fileList);
            }
            log.info(AnsiColors.CYAN + "=== PUBLISHING FINISHED ===\n" + AnsiColors.RESET);
        }

        return isAllSuccess;
    }


    private boolean processChunkWithRetry(UserActor actor, List<File> chunk, int chunkNumber) {
        List<String> attachmentIds = new ArrayList<>();
        boolean hasErrors = false;

        for (File file : chunk) {
            int attempt = 0;
            boolean fileSuccess = false;

            while (attempt < maxRetries && !fileSuccess) {
                attempt++;
                try {
                    String attachmentId = uploadPhotoWithBackoff(actor, file);
                    attachmentIds.add(attachmentId);
                    fileSuccess = true;
                    Thread.sleep(baseDelay);
                } catch (ApiException e) {
                    handleApiError(chunkNumber, e);
                    waitBeforeRetry(attempt);
                } catch (Exception e) {
                    log.severe(AnsiColors.RED + "Critical error: " + e.getMessage() + AnsiColors.RESET);
                }
            }

            if (!fileSuccess) {
                hasErrors = true;
                log.severe(AnsiColors.RED + String.format(
                        "File %s failed after %d attempts", file.getName(), maxRetries
                ) + AnsiColors.RESET);
            }
        }

        if (!attachmentIds.isEmpty()) {
            try {
                publishPost(actor, attachmentIds);
                return !hasErrors;
            } catch (Exception e) {
                log.severe(AnsiColors.RED + "Publish error: " + e.getMessage() + AnsiColors.RESET);
            }
        }
        return false;
    }


    private String uploadPhotoWithBackoff(UserActor actor, File file)
            throws ApiException, ClientException {
        GetWallUploadServerResponse server = vk.photos().getWallUploadServer(actor).execute();
        WallUploadResponse upload = vk.upload()
                .photoWall(server.getUploadUrl().toString(), file)
                .execute();

        SaveWallPhotoResponse photo = vk.photos()
                .saveWallPhoto(actor, upload.getPhoto())
                .server(upload.getServer())
                .hash(upload.getHash())
                .execute()
                .getFirst();

        log.info(AnsiColors.GREEN + String.format(
                "[%s] Uploaded successfully! Photo ID: %d_%d",
                file.getName(), photo.getOwnerId(), photo.getId()
        ) + AnsiColors.RESET);

        return "photo" + photo.getOwnerId() + "_" + photo.getId();
    }


    private void publishPost(UserActor actor, List<String> attachmentIds)
            throws ClientException, ApiException {
        String attachments = String.join(",", attachmentIds);
        PostResponse response = vk.wall().post(actor)
                .ownerId(groupId)
                .attachments(attachments)
                .execute();
        log.info(AnsiColors.GREEN + String.format(
                "Published post ID: %d | Images: %d",
                response.getPostId(), attachmentIds.size()
        ) + AnsiColors.RESET);
    }


    private int calculateBackoffDelay(int attempt) {
        return Math.min((int) (baseDelay * Math.pow(2, attempt)), maxDelay);
    }


    private void handleApiError(int chunkNumber, ApiException e) {
        if (e.getCode() == 6) {
            log.warning(AnsiColors.YELLOW + String.format(
                    "Chunk %d | VK API Limits: %s",
                    chunkNumber, e.getMessage()
            ) + AnsiColors.RESET);
        } else {
            log.warning(AnsiColors.YELLOW + e.getMessage() + AnsiColors.RESET);
        }
    }


    private void waitBeforeRetry(int attempt) {
        int delay = calculateBackoffDelay(attempt);
        try {
            log.info(AnsiColors.CYAN + String.format(
                    "Waiting %d ms before next attempt...", delay
            ) + AnsiColors.RESET);
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {}
    }


    // Вспомогательные методы
    private void removeDuplicates(List<WebImage> images) {
        Set<WebImage> unique = new LinkedHashSet<>(images);
        images.clear();
        images.addAll(unique);
    }


    private void deleteDownloadedFiles(List<File> files) {
        int deletedCount = 0;
        for (File file : files) {
            if (FileUtils.deleteQuietly(file)) {
                deletedCount++;
            }
        }
        log.info(AnsiColors.CYAN + String.format(
                "Deleted %d/%d temporary files", deletedCount, files.size()
        ) + AnsiColors.RESET);
    }


    private static <T> List<List<T>> chunkify(List<T> list, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return chunks;
    }
}