package ru.aikr.inet.parser.mlpublish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.logging.LogEventsPublisher;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.RecommendationDecision;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.util.HashUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ml/publish")
@RequiredArgsConstructor
@Slf4j
public class MlPublishController {

    private static final String ML_PREVIEW_EVENT = "ml-preview";

    private final MlRecommendationClient mlRecommendationClient;
    private final VKPublishService vkPublishService;
    private final VkHistoryService historyService;
    private final LogEventsPublisher logEventsPublisher;

    @PostMapping("/preview")
    public Mono<MlPublishPreviewResponse> preview(@RequestBody MlPublishPreviewRequest request) {
        List<WebImage> images = toWebImages(request.getImages());
        return mlRecommendationClient.recommend(images)
                .doOnNext(this::logPreviewSummary)
                .map(this::toPreviewResponse)
                .onErrorResume(MlRecommendationException.class, error -> {
                    log.warn("ML preview failed, returning fallback list", error);
                    return Mono.just(new MlPublishPreviewResponse(Collections.emptyList()));
                });
    }

    @PostMapping("/commit")
    public Mono<MlPublishCommitResponse> commit(@RequestBody MlPublishCommitRequest request) {
        List<MlPublishCommitItem> toPublishItems = request.getImages().stream()
                .filter(MlPublishCommitItem::isPublish)
                .collect(Collectors.toList());
        List<WebImage> toPublish = toPublishItems.stream()
                .map(item -> new WebImage(item.getId(), item.getUrl()))
                .collect(Collectors.toList());

        if (toPublish.isEmpty()) {
            return Mono.just(new MlPublishCommitResponse(0, 0, 0, 0));
        }

        return vkPublishService.generatePostsAndPublishToCommunityWall(toPublish)
                .doOnSuccess(result -> recordHistory(toPublishItems, toPublish))
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

    private void logPreviewSummary(List<MlRecommendation> recommendations) {
        int total = recommendations.size();
        long publish = countByDecision(recommendations, RecommendationDecision.PUBLISH);
        long review = countByDecision(recommendations, RecommendationDecision.REVIEW);
        long skip = countByDecision(recommendations, RecommendationDecision.SKIP);
        double maxScore = recommendations.stream()
                .mapToDouble(MlRecommendation::score)
                .max()
                .orElse(0d);

        log.info("ML preview: total={}, publish={}, review={}, skip={}, maxScore={}",
                total, publish, review, skip, maxScore);
        logEventsPublisher.publish(buildPreviewEvent(total, publish, review, skip, maxScore));
    }

    private long countByDecision(List<MlRecommendation> recommendations, RecommendationDecision decision) {
        return recommendations.stream()
                .filter(rec -> rec.decision() == decision)
                .count();
    }

    private String buildPreviewEvent(int total, long publish, long review, long skip, double maxScore) {
        return String.format(Locale.ROOT,
                "{\"event\":\"%s\",\"total\":%d,\"publish\":%d,\"review\":%d,\"skip\":%d,\"maxScore\":%.3f}",
                ML_PREVIEW_EVENT, total, publish, review, skip, maxScore);
    }

    private List<WebImage> toWebImages(List<MlPublishCandidate> images) {
        if (images == null) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(c -> new WebImage(c.getId(), c.getUrl()))
                .collect(Collectors.toList());
    }

    private void recordHistory(List<MlPublishCommitItem> commitItems, List<WebImage> published) {
        for (int i = 0; i < commitItems.size() && i < published.size(); i++) {
            MlPublishCommitItem item = commitItems.get(i);
            WebImage image = published.get(i);
            String url = image.getDirectLink();
            String hash = HashUtils.md5(url);

            VkImageHistoryRecord record = new VkImageHistoryRecord(
                    null,
                    url,
                    hash,
                    Instant.now()
            );
            historyService.recordPublication(
                    record,
                    item.getDecision() != null ? item.getDecision().name() : null,
                    item.getScore(),
                    item.getReason()
            );
        }
    }
}
