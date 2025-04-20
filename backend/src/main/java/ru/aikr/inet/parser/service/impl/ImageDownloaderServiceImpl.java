package ru.aikr.inet.parser.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.config.SecurityConfig;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.UserAgentService;

import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDownloaderServiceImpl implements ImageDownloaderService {

    private final UserAgentService userAgentService;
    private final SecurityConfig securityConfig;

    /** Папка по умолчанию из application.yml (env.parser.download-folder-name) */
    @Value("${env.parser.download-folder-name:downloaded}")
    private String defaultFolder;

    /** Сколько попыток делать при ошибках */
    @Value("${env.image-downloader.max-retries:3}")
    private int maxRetries;

    /** Абсолютный путь к папке по умолчанию */
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

    @Override
    public List<Path> downloadImages(List<WebImage> images, Path outputDir) {
        // выбираем целевую директорию
        Path targetDir = (outputDir != null)
                ? outputDir.toAbsolutePath().normalize()
                : defaultDownloadDir;

        // гарантированно создаём
        try {
            if (Files.notExists(targetDir)) {
                Files.createDirectories(targetDir);
                log.info("Created download directory: {}", targetDir);
            }
        } catch (Exception e) {
            log.error("Cannot create download directory '{}': {}", targetDir, e.getMessage());
            return List.of();
        }

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
    }

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

    private Path downloadSingle(String imageUrl, Path dir) throws Exception {
        String ua = userAgentService.getRandomUserAgent();
        SSLSocketFactory sslFactory = securityConfig.getUnsafeSSLContext().getSocketFactory();

        String fileName = deriveFileName(imageUrl);
        Path out = dir.resolve(fileName);

        try (InputStream in = Jsoup.connect(imageUrl)
                .sslSocketFactory(sslFactory)
                .userAgent(ua)
                .ignoreContentType(true)
                .execute()
                .bodyStream()) {

            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded {} → {} (UA: {})", imageUrl, out, shortenUA(ua));
        }
        return out;
    }

    private String deriveFileName(String url) throws Exception {
        URI uri = new URI(url);
        String raw = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        if (raw.contains("?")) raw = raw.substring(0, raw.indexOf('?'));
        return raw.replaceAll("[^\\w.-]", "_");
    }

    private String shortenUA(String ua) {
        return ua.length() <= 40 ? ua : ua.substring(0, 37) + "...";
    }
}