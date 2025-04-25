package ru.aikr.inet.parser.service;

import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;

import java.util.List;

/**
 * Сервис для публикации изображений в сообщество ВКонтакте.
 */
public interface VKPublishService {

    /**
     * Создаёт посты и публикует их в группе.
     *
     * @param images список картинок
     * @return Mono с числом успешно опубликованных изображений
     */
    Mono<Integer> generatePostsAndPublishToCommunityWall(List<WebImage> images);
}