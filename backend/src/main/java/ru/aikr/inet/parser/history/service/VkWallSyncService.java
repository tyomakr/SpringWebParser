package ru.aikr.inet.parser.history.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.vk.VkApiClient;
import ru.aikr.inet.parser.vk.VkApiProperties;
import ru.aikr.inet.parser.vk.dto.WallGetResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class VkWallSyncService {

    private static final int PAGE_SIZE = 100;

    private final VkApiClient vkApiClient;
    private final VkHistoryRepository repository;
    private final VkApiProperties apiProperties;
    private final Duration pageDelay;

    public VkWallSyncService(VkApiClient vkApiClient,
                             VkHistoryRepository repository,
                             VkApiProperties apiProperties) {
        this(vkApiClient, repository, apiProperties, Duration.ofMillis(350));
    }

    public VkWallSyncService(VkApiClient vkApiClient,
                             VkHistoryRepository repository,
                             VkApiProperties apiProperties,
                             Duration pageDelay) {
        this.vkApiClient = vkApiClient;
        this.repository = repository;
        this.apiProperties = apiProperties;
        this.pageDelay = pageDelay != null ? pageDelay : Duration.ZERO;
    }

    public VkWallSyncReport syncWall(Instant since, int pagesLimit) {
        int safePages = Math.max(pagesLimit, 1);
        Long ownerId = Objects.requireNonNull(apiProperties.getGroupId(), "vk.api.group-id must be configured");
        int postsFetched = 0;
        int photosFound = 0;
        int inserted = 0;
        int skipped = 0;
        boolean stop = false;

        for (int pageIndex = 0; pageIndex < safePages; pageIndex++) {
            if (stop) {
                break;
            }
            int offset = pageIndex * PAGE_SIZE;
            WallGetResponse response = vkApiClient.wallGet(ownerId, PAGE_SIZE, offset).block();
            if (response == null || response.getResponse() == null) {
                break;
            }
            List<WallGetResponse.WallPost> items = response.getResponse().getItems();
            if (items == null || items.isEmpty()) {
                break;
            }
            for (WallGetResponse.WallPost post : items) {
                if (post == null) {
                    continue;
                }
                Instant createdAt = Instant.ofEpochSecond(post.getDate() != null ? post.getDate() : 0L);
                if (since != null && createdAt.isBefore(since)) {
                    stop = true;
                    break;
                }
                postsFetched++;
                List<WallGetResponse.Attachment> attachments = post.getAttachments();
                if (attachments == null) {
                    continue;
                }
                for (WallGetResponse.Attachment attachment : attachments) {
                    if (attachment == null || !"photo".equals(attachment.getType())) {
                        continue;
                    }
                    WallGetResponse.Photo photo = attachment.getPhoto();
                    if (photo == null || photo.getSizes() == null || photo.getSizes().isEmpty()) {
                        continue;
                    }
                    Optional<WallGetResponse.PhotoSize> bestSize = photo.getSizes().stream()
                            .filter(Objects::nonNull)
                            .filter(size -> StringUtils.hasText(size.getUrl()))
                            .max(Comparator.comparingInt(this::sizeArea));
                    if (bestSize.isEmpty()) {
                        continue;
                    }
                    String url = bestSize.get().getUrl();
                    photosFound++;
                    String hash = hashUrl(url);
                    VkImageHistoryRecord record = new VkImageHistoryRecord(post.getId(), url, hash, createdAt);
                    record.setUseForTraining(Boolean.TRUE);
                    boolean saved = repository.saveIfAbsent(record);
                    if (saved) {
                        inserted++;
                    } else {
                        skipped++;
                    }
                }
            }
            if (pageIndex < safePages - 1 && !stop) {
                pauseBetweenPages();
            }
        }
        return new VkWallSyncReport(postsFetched, photosFound, inserted, skipped);
    }

    protected void pauseBetweenPages() {
        if (pageDelay == null || pageDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(pageDelay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private int sizeArea(WallGetResponse.PhotoSize size) {
        int width = size.getWidth() != null ? size.getWidth() : 0;
        int height = size.getHeight() != null ? size.getHeight() : 0;
        return width * height;
    }

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is not available", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0xF, 16));
            builder.append(Character.forDigit(current & 0xF, 16));
        }
        return builder.toString().toLowerCase();
    }
}
