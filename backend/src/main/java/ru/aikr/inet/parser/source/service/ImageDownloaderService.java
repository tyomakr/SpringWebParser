package ru.aikr.inet.parser.source.service;

import ru.aikr.inet.parser.model.WebImage;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

/**
 * Скачивает список WebImage в указанный каталог реактивно.
 */
public interface ImageDownloaderService {
    /**
     * Скачать все изображения реактивно.
     *
     * @param images    список WebImage (getDirectLink(), getId() и т.п.)
     * @param outputDir каталог для сохранения (если null — берётся настройка)
     * @return Mono со списком путей к загруженным файлам
     */
    Mono<List<Path>> downloadImages(List<WebImage> images, Path outputDir);
}
