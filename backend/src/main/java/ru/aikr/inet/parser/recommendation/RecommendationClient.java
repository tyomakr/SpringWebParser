package ru.aikr.inet.parser.recommendation;

import ru.aikr.inet.parser.model.WebImage;

import java.util.List;

/**
 * Клиент, который оборачивает вызов внешнего ML-сервиса `/recommend`.
 */
public interface RecommendationClient {

    /**
     * Отправляет список изображений и получает список рекомендаций.
     *
     * @param candidates исходные WebImage
     * @return список рекомендаций по каждому изображению
     */
    List<RecommendationResult> recommend(List<WebImage> candidates);
}
