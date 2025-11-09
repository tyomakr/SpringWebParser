package ru.aikr.inet.parser.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.RecommendationClient;
import ru.aikr.inet.parser.recommendation.RecommendationDecision;
import ru.aikr.inet.parser.recommendation.RecommendationException;
import ru.aikr.inet.parser.recommendation.RecommendationResult;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/sites/fishki")
@Validated
public class FishkiRestController {

    private final WebImageService webImageService;
    private final VKPublishService vkPublishService;
    private final RecommendationClient recommendationClient;

    private static final int MAX_PAGES_RANGE = 100;
    private static final int MAX_IMAGES_TO_PUBLISH = 100;

    public FishkiRestController(WebImageService webImageService,
                                VKPublishService vkPublishService,
                                @Nullable RecommendationClient recommendationClient) {
        this.webImageService = webImageService;
        this.vkPublishService = vkPublishService;
        this.recommendationClient = recommendationClient;
    }

    /** Возвращает Flux картинок с fishki.net вместо List */
    @GetMapping("/images/{num1}/to/{num2}")
    public Flux<WebImage> getImagesFromPages(
            @PathVariable
            @Min(value = 1, message = "Страница начала должна быть не менее 1")
            @Max(value = 10000, message = "Страница начала не может быть больше 10000")
            int num1,
            @PathVariable
            @Min(value = 1, message = "Страница конца должна быть не менее 1")
            @Max(value = 10000, message = "Страница конца не может быть больше 10000")
            int num2) {

        if (num1 > num2) {
            return Flux.error(new IllegalArgumentException(
                    String.format("Страница начала (%d) не может быть больше страницы конца (%d)", num1, num2)
            ));
        }

        int range = num2 - num1 + 1;
        if (range > MAX_PAGES_RANGE) {
            return Flux.error(new IllegalArgumentException(
                    String.format("Диапазон страниц (%d) слишком большой. Максимум: %d", range, MAX_PAGES_RANGE)
            ));
        }

        log.info("Parsing pages from {} to {} (range: {})", num1, num2, range);
        return webImageService.getImagesFromPages(num1, num2);
    }

    /** Сохраняет и публикует выбранные на ВКонтакте реактивно */
    @PostMapping(path = {"/images", "/images/"})
    public Mono<ResponseEntity<String>> saveAndPublish(
            @RequestBody
            @NotEmpty(message = "Список изображений не может быть пустым")
            @Size(max = MAX_IMAGES_TO_PUBLISH, message = "Максимальное количество изображений: " + MAX_IMAGES_TO_PUBLISH)
            @Valid
            List<@Valid WebImage> images) {

        logRecommendations(images);
        log.info("Publishing {} images to VK (recommendation enabled)", images.size());
        return vkPublishService.generatePostsAndPublishToCommunityWall(images)
                .map(result -> {
                    String message;
                    if (result.isSuccess()) {
                        message = String.format("Успешно опубликовано %d изображений в %d постах",
                                result.getPublishedCount(), result.getPostsPublished());
                    } else if (result.isPartialSuccess()) {
                        message = String.format(
                                "Частичный успех: загружено %d, опубликовано %d изображений в %d постах. " +
                                        "Не удалось опубликовать %d постов.",
                                result.getUploadedCount(), result.getPublishedCount(),
                                result.getPostsPublished(), result.getPostsFailed());
                        if (result.getErrorMessage() != null) {
                            message += " Ошибки: " + result.getErrorMessage();
                        }
                    } else {
                        message = String.format("Ошибка публикации. Загружено: %d, опубликовано: %d",
                                result.getUploadedCount(), result.getPublishedCount());
                        if (result.getErrorMessage() != null) {
                            message += ". " + result.getErrorMessage();
                        }
                    }

                    HttpStatus status = result.isSuccess() ? HttpStatus.OK
                            : result.isPartialSuccess() ? HttpStatus.ACCEPTED
                            : HttpStatus.INTERNAL_SERVER_ERROR;

                    return ResponseEntity.status(status).body(message);
                })
                .onErrorResume(error -> {
                    log.error("Error in VK publish: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Ошибка публикации: " + error.getMessage()));
                });
    }

    private void logRecommendations(List<WebImage> images) {
        if (recommendationClient == null || images.isEmpty()) {
            return;
        }

        try {
            List<RecommendationResult> recommendations = recommendationClient.recommend(images);
            long publish = recommendations.stream()
                    .filter(item -> item.recommendation() == RecommendationDecision.PUBLISH)
                    .count();
            long review = recommendations.stream()
                    .filter(item -> item.recommendation() == RecommendationDecision.REVIEW)
                    .count();
            long skip = recommendations.stream()
                    .filter(item -> item.recommendation() == RecommendationDecision.SKIP)
                    .count();

            RecommendationResult sample = recommendations.stream().findFirst().orElse(null);
            String sampleInfo = sample == null ? "no recommendations" :
                    String.format("sample %s=%.2f (%s)", sample.id(), sample.score(), sample.reason());
            log.info("Recommendation summary: publish={}, review={}, skip={} | {}", publish, review, skip, sampleInfo);
        } catch (RecommendationException ex) {
            log.warn("Recommendation service error, continuing without ML data: {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Recommendation client failed", ex);
        }
    }
}
