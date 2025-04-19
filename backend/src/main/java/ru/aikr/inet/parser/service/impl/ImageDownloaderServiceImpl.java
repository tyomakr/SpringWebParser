package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.config.SecurityConfig;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.UserAgentService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageDownloaderServiceImpl implements ImageDownloaderService {

    private static final Logger log = Logger.getLogger(ImageDownloaderServiceImpl.class.getName());

    private final UserAgentService userAgentService;
    private final SecurityConfig securityConfig;

    @Value("${env.image-downloader.max-retries}")
    private int maxRetries;

    @Override
    public List<Path> downloadImages(List<WebImage> images, Path outputDir) {
        return images.stream()
                .map(image -> processImage(image.getDirectLink(), outputDir))
                .collect(Collectors.toList());
    }


    private Path processImage(String imageUrl, Path outputDir) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return downloadSingleImage(imageUrl, outputDir);
            } catch (Exception e) {
                handleDownloadError(imageUrl, attempt, e); // Централизованная обработка
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed after " + maxRetries + " attempts: " + imageUrl, e);
                }
            }
        }
        throw new IllegalStateException("Unexpected error");
    }


    private Path downloadSingleImage(String url, Path outputDir) throws IOException, URISyntaxException {
        String userAgent = userAgentService.getRandomUserAgent();
        String shortenedUA = userAgent.length() > 35
                ? userAgent.substring(0, 35) + "..."
                : userAgent;

        Path outputPath = outputDir.resolve(generateFileName(url));

        try (InputStream is = Jsoup.connect(url)
                .sslSocketFactory(getUnsafeSSLSocketFactory())
                .userAgent(userAgent)
                .ignoreContentType(true)
                .execute()
                .bodyStream()) {

            Files.copy(is, outputPath);
            log.info(AnsiColors.GREEN + String.format(
                    "Downloaded: %-40s | UA: %s",
                    outputPath.getFileName(),
                    shortenedUA
            ) + AnsiColors.RESET);

        } catch (Exception e) {
            log.severe(AnsiColors.RED + String.format(
                    "Failed: %-40s | UA: %s | Error: %s",
                    outputPath.getFileName(),
                    shortenedUA,
                    e.getMessage()
            ) + AnsiColors.RESET);
            throw e;
        }
        return outputPath;
    }


    private String generateFileName(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String path = uri.getPath();
        String rawName = path.substring(path.lastIndexOf('/') + 1);
        return sanitizeFileName(rawName.split("[?]")[0]);
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }


    private void handleDownloadError(String url, int attempt, Exception e) {
        String message = String.format(
                "Attempt %d/%d failed: %s | URL: %s",
                attempt, maxRetries, e.getMessage(), url
        );
        log.warning(AnsiColors.YELLOW + message + AnsiColors.RESET);
    }

    /**
     * Получает SSL-фабрику с обработкой исключений.
     */
    private javax.net.ssl.SSLSocketFactory getUnsafeSSLSocketFactory() {
        try {
            return securityConfig.getUnsafeSSLContext().getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.severe(AnsiColors.RED + "SSL Error: " + e.getMessage() + AnsiColors.RESET);
            throw new RuntimeException("Failed to initialize SSL context", e);
        }
    }
}