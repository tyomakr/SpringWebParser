package ru.aikr.inet.parser.service;

import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;

import java.util.List;

/**
 * Сервис для публикации изображений в сообщество ВКонтакте.
 */
public interface VKPublishService {

    /**
     * Генерирует посты и публикует их во ВКонтакте.
     *
     * @param fullImagesList список изображений для публикации
     * @return Mono, сигнализирующий об успехе (true) или неуспехе (false) всей операции
     */
    Mono<Boolean> generatePostsAndPublishToCommunityWall(List<WebImage> fullImagesList);
}