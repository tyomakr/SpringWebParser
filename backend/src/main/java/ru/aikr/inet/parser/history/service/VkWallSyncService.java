package ru.aikr.inet.parser.history.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.model.VkSyncProperties;
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
import java.util.stream.Stream;

@Service
public class VkWallSyncService {

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(Throwable cause) {
            super(cause);
        }
    }

    private final VkApiClient vkApiClient;
    private final VkHistoryRepository repository;
    private final VkApiProperties apiProperties;
    private final VkSyncProperties syncProperties;
    private final Duration pageDelay = Duration.ofMillis(350);
    private final Counter postsCounter;
    private final Counter photosCounter;
    private final Counter insertedCounter;
    private final Counter skippedCounter;

    public VkWallSyncService(VkApiClient vkApiClient,
                             VkHistoryRepository repository,
                             VkApiProperties apiProperties,
                             VkSyncProperties syncProperties,
                             MeterRegistry meterRegistry) {
        this.vkApiClient = vkApiClient;
        this.repository = repository;
        this.apiProperties = apiProperties;
        this.syncProperties = syncProperties;
        this.postsCounter = meterRegistry.counter("vk.wall.sync.posts");
        this.photosCounter = meterRegistry.counter("vk.wall.sync.photos");
        this.insertedCounter = meterRegistry.counter("vk.wall.sync.inserted");
        this.skippedCounter = meterRegistry.counter("vk.wall.sync.skipped");
    }

    public VkWallSyncReport syncWall(Instant since, int pagesLimit) {
        int safePages = pagesLimit <= 0 ? Integer.MAX_VALUE : pagesLimit;
        Long ownerId = Objects.requireNonNull(apiProperties.getGroupId(),
                "vk.api.group-id must be configured");
        int pageSize = Math.max(1, syncProperties.getPageSize());
        int postsFetched = 0;
        int photosFound = 0;
        int inserted = 0;
        int skipped = 0;
        boolean stop = false;

        for (int pageIndex = 0; pageIndex < safePages; pageIndex++) {
            if (stop) {
                break;
            }
            int offset = pageIndex * pageSize;
            WallGetResponse response = fetchWallPage(ownerId, pageSize, offset);
            if (response == null || response.getResponse() == null) {
                break;
            }
            List<WallGetResponse.WallPost> items = response.getResponse().getItems();
            if (items == null || items.isEmpty()) {
                break;
            }
            boolean pageInserted = false;
            Instant latestCreated = null;
            for (WallGetResponse.WallPost post : items) {
                if (post == null) {
                    continue;
                }
                Instant createdAt = instantFrom(post.getDate());
                latestCreated = latestCreated == null ? createdAt : latestCreated;
                postsFetched++;
                List<WallGetResponse.Attachment> attachments = attachmentsFrom(post).toList();
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
                    photosFound++;
                    String url = bestSize.get().getUrl();
                    String hash = hashUrl(url);
                    VkImageHistoryRecord record = new VkImageHistoryRecord(post.getId(), url, hash, createdAt);
                    record.setUseForTraining(Boolean.TRUE);
                    boolean saved = repository.saveIfAbsent(record);
                    if (saved) {
                        inserted++;
                        pageInserted = true;
                    } else {
                        skipped++;
                    }
                }
            }
            if (!pageInserted && since != null && latestCreated != null && latestCreated.isBefore(since)) {
                stop = true;
            }
            if (pageIndex < safePages - 1 && !stop) {
                pauseBetweenPages();
            }
        }

        postsCounter.increment(postsFetched);
        photosCounter.increment(photosFound);
        insertedCounter.increment(inserted);
        skippedCounter.increment(skipped);
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

    private WallGetResponse fetchWallPage(Long ownerId, int count, int offset) {
        try {
            return vkApiClient.wallGet(ownerId, count, offset).block();
        } catch (WebClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            if (isRateLimit(body)) {
                throw new RateLimitException(ex);
            }
            throw ex;
        }
    }

    private Stream<WallGetResponse.Attachment> attachmentsFrom(WallGetResponse.WallPost post) {
        Stream<WallGetResponse.Attachment> direct = Optional.ofNullable(post.getAttachments())
                .map(List::stream)
                .orElseGet(Stream::empty);
        Stream<WallGetResponse.Attachment> copied = Optional.ofNullable(post.getCopyHistory())
                .map(List::stream)
                .orElseGet(Stream::empty)
                .flatMap(this::attachmentsFrom);
        return Stream.concat(direct, copied);
    }

    private boolean isRateLimit(String payload) {
        if (payload == null) {
            return false;
        }
        return payload.contains("\"error_code\":6") || payload.contains("\"error_code\":29");
    }

    private Instant instantFrom(Long epochSeconds) {
        long value = epochSeconds != null ? epochSeconds : 0L;
        return Instant.ofEpochSecond(value);
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
