package ru.aikr.inet.parser.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.UserAgentService;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Mono;

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
     * Скачиваем список WebImage в папку targetDir (или default).
     * Оригинальный синхронный метод, обёрнутый в Mono.
     */
    @Override
    public Mono<List<Path>> downloadImages(List<WebImage> images, Path outputDir) {
        Path targetDir = (outputDir != null)
                ? outputDir.toAbsolutePath().normalize()
                : defaultDownloadDir;

        // Обёртка в Mono, чтобы не блокировать Netty-пулы
        return Mono.fromCallable(() -> {
            List<Path> results = new ArrayList<>();
            for (WebImage wi : images) {
                Path file = downloadWithRetries(wi.getDirectLink(), targetDir);
                if (file != null && Files.exists(file)) {
                    results.add(file);
                } else {
                    log.warn("Skipping missing file: {}", file);
                }
            }
            return results;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /** Пытаемся скачать до maxRetries раз */
    private Path downloadWithRetries(String url, Path dir) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return downloadSingle(url, dir);
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for {}: {}", attempt, maxRetries, url, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Giving up downloading {} after {} attempts", url, maxRetries);
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