package ru.aikr.inet.parser.mlpublish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.dto.VKPublishResult;
import ru.aikr.inet.parser.history.VkHistoryService;
import ru.aikr.inet.parser.history.VkImageHistoryRecord;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.util.HashUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ml/publish")
@RequiredArgsConstructor
@Slf4j
public class MlPublishController {

    private final MlRecommendationClient mlRecommendationClient;
    private final VKPublishService vkPublishService;
    private final VkHistoryService historyService;

    @PostMapping("/preview")
    public Mono<MlPublishPreviewResponse> preview(@RequestBody MlPublishPreviewRequest request) {
        List<WebImage> images = toWebImages(request.getImages());
        return mlRecommendationClient.recommend(images)
                .map(this::toPreviewResponse)
                .onErrorResume(MlRecommendationException.class, error -> {
                    log.warn("ML preview failed, returning fallback list", error);
                    return Mono.just(new MlPublishPreviewResponse(Collections.emptyList()));
                });
    }

    @PostMapping("/commit")
    public Mono<MlPublishCommitResponse> commit(@RequestBody MlPublishCommitRequest request) {
        List<WebImage> toPublish = request.getImages().stream()
                .filter(MlPublishCommitItem::isPublish)
                .map(item -> new WebImage(item.getId(), item.getUrl()))
                .collect(Collectors.toList());

        if (toPublish.isEmpty()) {
            return Mono.just(new MlPublishCommitResponse(0, 0, 0, 0));
        }

        return vkPublishService.generatePostsAndPublishToCommunityWall(toPublish)
                .doOnSuccess(result -> recordHistory(toPublish, result))
                .map(result -> new MlPublishCommitResponse(
                        result.getUploadedCount(),
                        result.getPublishedCount(),
                        result.getPostsPublished(),
                        result.getPostsFailed()
                ));
    }

    private MlPublishPreviewResponse toPreviewResponse(List<MlRecommendation> recommendations) {
        List<MlPublishPreviewItem> items = recommendations.stream()
                .sorted(Comparator.comparingDouble(MlRecommendation::score).reversed())
                .map(rec -> new MlPublishPreviewItem(
                        rec.id(),
                        rec.url(),
                        rec.score(),
                        rec.reason(),
                        rec.decision().name()
                ))
                .collect(Collectors.toList());
        return new MlPublishPreviewResponse(items);
    }

    private List<WebImage> toWebImages(List<MlPublishCandidate> images) {
        if (images == null) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(c -> new WebImage(c.getId(), c.getUrl()))
                .collect(Collectors.toList());
    }

    private void recordHistory(List<WebImage> published, VKPublishResult publishResult) {
        // Минимальная реализация: пишем только hash + url, postId пока можно оставить null.
        published.forEach(image -> {
            // ВОЗМОЖНО, тебе нужно заменить getDirectLink() на getUrl(),
            // если в WebImage нет поля directLink. Подправь под свой класс.
            String url = image.getDirectLink(); // если нет такого метода — используй image.getUrl()
            String hash = HashUtils.md5(url);

            VkImageHistoryRecord record = new VkImageHistoryRecord(
                    null,   // postId (можно позже заполнить данными из publishResult)
                    url,
                    hash,
                    Instant.now()
            );
            historyService.recordPublication(record);
        });
    }
}
