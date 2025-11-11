package ru.aikr.inet.parser.fishki.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.fishki.service.ImageDownloaderService;
import ru.aikr.inet.parser.fishki.service.UserAgentService;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDownloaderServiceImpl implements ImageDownloaderService {

    private final UserAgentService userAgentService;
    private final SSLContext       sslContext;

    /** Папка по умолчанию */
    @Value("${env.parser.download-folder-name:downloaded}")
    private String defaultFolder;

    /** Сколько попыток делать при ошибках */
    @Value("${env.image-downloader.max-retries:3}")
    private int maxRetries;

    /** Максимальное количество параллельных загрузок */
    @Value("${env.image-downloader.max-concurrency:10}")
    private int maxConcurrency;

    private Path defaultDownloadDir;

    @PostConstruct
    public void init() {
        defaultDownloadDir = Paths.get(defaultFolder).toAbsolutePath().normalize();
        try {
            if (Files.notExists(defaultDownloadDir)) {
                Files.createDirectories(defaultDownloadDir);
                log.info("Created default download folder: {}", defaultDownloadDir);
            }
        } catch (Exception e) {
            log.error("Could not create default download folder '{}': {}", defaultDownloadDir, e.getMessage());
        }
    }

    /**
     * Скачиваем список WebImage в папку targetDir (или default) параллельно.
     * Использует Flux для параллельной обработки с ограничением конкурентности.
     */
    @Override
    public Mono<List<Path>> downloadImages(List<WebImage> images, Path outputDir) {
        if (images == null || images.isEmpty()) {
            log.warn("Empty image list provided for download");
            return Mono.just(List.of());
        }

        Path targetDir = (outputDir != null)
                ? outputDir.toAbsolutePath().normalize()
                : defaultDownloadDir;

        log.info("Starting parallel download of {} images (max concurrency: {})", 
                images.size(), maxConcurrency);

        // Создаем Flux из списка изображений и обрабатываем параллельно
        return Flux.fromIterable(images)
                .flatMap(wi -> 
                    Mono.fromCallable(() -> {
                        try {
                            return downloadWithRetries(wi.getDirectLink(), targetDir);
                        } catch (Exception e) {
                            log.error("Unexpected error downloading {}: {}", 
                                    wi.getDirectLink(), e.getMessage());
                            return null;
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnSuccess(path -> {
                        if (path != null && Files.exists(path)) {
                            log.debug("Successfully downloaded: {}", path);
                        } else {
                            log.warn("Failed to download: {}", wi.getDirectLink());
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Error downloading {}: {}", wi.getDirectLink(), e.getMessage());
                        return Mono.just((Path) null);
                    }),
                    maxConcurrency  // ограничиваем количество параллельных загрузок
                )
                .filter(Objects::nonNull)
                .filter(Files::exists)
                .collectList()
                .doOnSuccess(results -> 
                    log.info("Download completed: {}/{} images successfully downloaded", 
                            results.size(), images.size())
                )
                .doOnError(error -> 
                    log.error("Fatal error during parallel download: {}", error.getMessage())
                );
    }

    /** Пытаемся скачать до maxRetries раз */
    private Path downloadWithRetries(String url, Path dir) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Path result = downloadSingle(url, dir);
                if (attempt > 1) {
                    log.info("Successfully downloaded {} on attempt {}/{}", url, attempt, maxRetries);
                }
                return result;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("Attempt {}/{} failed for {}: {}", attempt, maxRetries, url, e.getMessage());
                } else {
                    log.error("Giving up downloading {} after {} attempts: {}", url, maxRetries, e.getMessage());
                }
            }
        }
        return null;
    }

    /** Одна попытка скачивания через Jsoup + SSL + UA */
    private Path downloadSingle(String imageUrl, Path dir) throws Exception {
        String ua = userAgentService.getRandomUserAgent();
        String raw = new URI(imageUrl).getPath();
        String name = Paths.get(raw).getFileName().toString();
        Path out = dir.resolve(name);

        try (InputStream in = Jsoup.connect(imageUrl)
                .sslSocketFactory(sslContext.getSocketFactory())
                .userAgent(ua)
                .ignoreContentType(true)
                .execute()
                .bodyStream()) {

            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded {} → {} (UA: {})", imageUrl, out, ua);
        }
        return out;
    }
}
