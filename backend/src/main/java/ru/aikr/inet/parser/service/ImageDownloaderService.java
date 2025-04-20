package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.model.WebImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Скачивает список WebImage в указанный каталог.
 * Если outputDir == null, используется папка, заданная в настройках.
 */
public interface ImageDownloaderService {
    /**
     * Скачать все изображения.
     *
     * @param images    список WebImage (getDirectLink(), getId() и т.п.)
     * @param outputDir каталог для сохранения (если null — берётся настройка)
     * @return список путей к загруженным файлам
     */
    List<Path> downloadImages(List<WebImage> images, Path outputDir);
}
