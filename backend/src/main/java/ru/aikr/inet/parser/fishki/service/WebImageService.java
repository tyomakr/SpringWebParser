package ru.aikr.inet.parser.fishki.service;

import ru.aikr.inet.parser.model.WebImage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

/**
 * Реактивный сервис для работы с WebImage.
 */
public interface WebImageService {
    /**
     * Возвращает поток WebImage для страниц [startPage..endPage].
     */
    Flux<WebImage> getImagesFromPages(int startPage, int endPage);

    /**
     * Скачивает изображения по URL из webImageList в указанную папку
     * и возвращает список путей к скачанным файлам.
     */
    Mono<List<Path>> downloadImagesFromWebImageLinks(List<WebImage> webImageList, Path targetDir);
}
