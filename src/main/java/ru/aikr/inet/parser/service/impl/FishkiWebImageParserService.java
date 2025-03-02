package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.WebImageParserService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class FishkiWebImageParserService implements WebImageParserService {

    private static final Logger log = Logger.getLogger("FishkiParserService");
    private static final int MAX_RETRIES = 5;
    private static final int MIN_DELAY_MS = 1500;
    private static final int MAX_DELAY_MS = 4000;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit...",
            "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/118.0"
    };

    @Value("${sites.fishki-url}")
    private String fishkiUrl;

    @Value("${sites.fishki-div-container-with-image}")
    private String divContainerWithImage;

    @Value("${env.parser.download-folder-name}")
    private String downloadFolder;

    private final Random random = new Random();

    private SSLContext getUnsafeSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        }, new SecureRandom());
        return sslContext;
    }

    // Основные методы
    @Override
    public List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd) {
        List<WebImage> resultList = new ArrayList<>();
        for (int i = pageBegin; i <= pageEnd; i++) {
            parsePage(i, resultList);
            humanDelay();
        }
        return resultList;
    }


    private void parsePage(int pageNumber, List<WebImage> resultList) {
        String url = fishkiUrl + pageNumber;
        log.info("Parsing page: " + pageNumber);

        String ua = getRandomUserAgent();
        log.info("Current UserAgent value: " + ua);

        try {
            Connection connection = Jsoup.connect(url)
                    .userAgent(ua)
                    .sslSocketFactory(getUnsafeSSLContext().getSocketFactory()) // Добавлено
                    .headers(getBrowserHeaders())
                    .ignoreContentType(true)
                    .timeout(15000);


            Document doc = connection.get();
            processElements(doc.select(divContainerWithImage), resultList);

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            log.warning("Page parse error: " + e.getMessage());
        }
    }

    private void processElements(List<Element> elements, List<WebImage> result) {
        for (Element element : elements) {
            String link = element.children().attr("abs:href");
            if (isValidUrl(link)) {
                result.add(new WebImage(link));
            }
        }
    }

    @Override
    public List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList) {
        List<File> files = new ArrayList<>();
        try {
            Path dir = Files.createDirectories(Paths.get(downloadFolder));
            for (WebImage image : webImageList) {
                processImage(image, dir, files);
            }
        } catch (IOException e) {
            log.severe("Directory error: " + e.getMessage());
        }
        return files;
    }

    private void processImage(WebImage image, Path dir, List<File> files) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                URI uri = new URI(image.getDirectLink());
                Path outputPath = dir.resolve(sanitizeFileName(uri.getPath()));

                if (Files.notExists(outputPath)) {
                    downloadFile(uri, outputPath);
                    log.info("Downloaded: " + outputPath.getFileName());
                }
                files.add(outputPath.toFile());
                break;

            } catch (URISyntaxException | IOException e) {
                handleDownloadError(image, attempt, e);
            }
        }
    }

    private void downloadFile(URI uri, Path outputPath) throws IOException {

        String ua = getRandomUserAgent();
        log.info("Current UserAgent value: " + ua);

        try (InputStream is = Jsoup.connect(uri.toString())
                .userAgent(ua)
                .sslSocketFactory(getUnsafeSSLContext().getSocketFactory()) // Добавлено
                .ignoreContentType(true)
                .execute()
                .bodyStream()) {

            Files.copy(is, outputPath);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("SSL error: " + e.getMessage());
        }
    }


    // Вспомогательные методы
    private String getRandomUserAgent() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    private Map<String, String> getBrowserHeaders() {
        return Map.of(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cache-Control", "no-cache"
        );
    }

    private void humanDelay() {
        try {
            Thread.sleep(MIN_DELAY_MS + random.nextInt(MAX_DELAY_MS - MIN_DELAY_MS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private boolean isValidUrl(String url) {
        return url != null && url.startsWith("http");
    }

    private void handleDownloadError(WebImage image, int attempt, Exception e) {
        if (attempt == MAX_RETRIES) {
            log.severe("Failed to download: " + image.getDirectLink());
        } else {
            log.warning(String.format("Attempt %d/%d failed: %s",
                    attempt, MAX_RETRIES, e.getMessage()));
        }
    }
}