package ru.aikr.inet.parser.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.WebImageParserService;
import ru.aikr.inet.parser.util.AnsiColors;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FishkiWebImageParserService implements WebImageParserService {

    private static final Logger log = Logger.getLogger("FishkiParserService");

    private static String[] USER_AGENTS = {};

    @Value("${sites.fishki-url}")
    private String fishkiUrl;

    @Value("${sites.fishki-div-container-with-image}")
    private String divContainerWithImage;

    @Value("${env.parser.download-folder-name}")
    private String downloadFolder;

    @Value("${env.parser.proxy.proxy-url}")
    private String proxyUrl;

    @Value("${env.parser.proxy.proxy-port}")
    private String proxyPort;

    @Value("${env.parser.user-agents-file}")
    private String userAgentsFile;

    @Value("${env.parser.max-retries}")
    private int maxRetries;

    @Value("${env.parser.min-parse-delay-ms}")
    private int minDelayMs;

    @Value("${env.parser.max-parse-delay-ms}")
    private int maxDelayMs;

    private final Random random = new Random();


    // Инициализация
    @PostConstruct
    public void init() {
        loadUserAgents();
        log.info(AnsiColors.CYAN + "=".repeat(50) + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + "Fishki Parser initialized" + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + String.format(
                "Config: URL=%s | Selector=%s", fishkiUrl, divContainerWithImage
        ) + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + "=".repeat(50) + AnsiColors.RESET);
    }

    private void loadUserAgents() {
        try {
            File file = ResourceUtils.getFile(userAgentsFile);
            List<String> agents = Files.readAllLines(file.toPath());
            USER_AGENTS = agents.toArray(new String[0]);
            log.info(AnsiColors.CYAN + String.format(
                    "Loaded %d User-Agents from %s", USER_AGENTS.length, userAgentsFile
            ) + AnsiColors.RESET);
        } catch (IOException e) {
            log.severe(AnsiColors.RED + "User-Agent load error: " + e.getMessage() + AnsiColors.RESET);
        }
    }


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


    @Override
    public List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd) {
        List<WebImage> resultList = new ArrayList<>();
        log.info(AnsiColors.CYAN + "\n=== PARSING PAGES %d-%d ===".formatted(pageBegin, pageEnd) + AnsiColors.RESET);

        for (int i = pageBegin; i <= pageEnd; i++) {
            log.info(AnsiColors.CYAN + "-".repeat(40) + AnsiColors.RESET);
            parsePage(i, resultList);
            humanDelay();
        }
        return resultList;
    }


    private void parsePage(int pageNumber, List<WebImage> resultList) {
        String url = fishkiUrl + pageNumber;
        String ua = getRandomUserAgent();

        log.info(AnsiColors.CYAN + String.format(
                "Parsing page %d\nURL: %s\nUser-Agent: %s",
                pageNumber, url, ua.substring(0, Math.min(40, ua.length())) + "..." + AnsiColors.RESET
        ));

        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, getTrustAllCerts(), new SecureRandom());

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(ua)
                    .sslSocketFactory(sslContext.getSocketFactory())
                    .headers(getBrowserHeaders())
                    .ignoreContentType(true)
                    .timeout(30000)
                    .followRedirects(true)
                    .execute();

            log.info(AnsiColors.CYAN + String.format(
                    "Response: %d %s",
                    response.statusCode(), response.statusMessage()
            ) + AnsiColors.RESET);

            // Обработка редиректов
            if (response.hasHeader("Location")) {
                String redirectUrl = response.header("Location");
                log.warning(AnsiColors.YELLOW + String.format(
                        "Redirect detected → %s", redirectUrl
                ) + AnsiColors.RESET);
                parsePage(extractPageNumber(redirectUrl), resultList);
                return;
            }

            // Парсинг контента
            Document doc = response.parse();
            processElements(doc.select(divContainerWithImage), resultList);

            if (resultList.isEmpty()) {
                log.warning(AnsiColors.YELLOW + String.format(
                        "No images found! Check selector: %s", divContainerWithImage
                ) + AnsiColors.RESET);
            } else {
                log.info(AnsiColors.GREEN + String.format(
                        "Found %d images", resultList.size()
                ) + AnsiColors.RESET);
            }

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            handlePageError(pageNumber, url, e);
            retryWithProxy(url);
        }
    }


    private void handlePageError(int pageNumber, String url, Exception e) {
        log.severe(AnsiColors.RED + String.format(
                "Page %d error: %s\nURL: %s",
                pageNumber, e.getMessage(), url
        ) + AnsiColors.RESET);
    }



    // Извлечение номера страницы из URL
    private int extractPageNumber(String url) {
        try {
            Matcher matcher = Pattern.compile("/(\\d+)/?$").matcher(url);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }


    // Доверяем всем сертификатам
    private TrustManager[] getTrustAllCerts() {
        return new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
    }

    // Повтор через прокси
    private void retryWithProxy(String url) {
        try {
            log.info(AnsiColors.CYAN + "Retrying with proxy..." + AnsiColors.RESET);
            Connection connection = Jsoup.connect(url)
                    .proxy(proxyUrl, Integer.parseInt(proxyPort))
                    .timeout(30000);
            connection.execute();
        } catch (IOException e) {
            log.severe(AnsiColors.RED + String.format(
                    "Proxy retry failed: %s | URL: %s", e.getMessage(), url
            ) + AnsiColors.RESET);
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

    // Загрузка изображений с повторами
    @Override
    public List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList) {
        List<File> files = new ArrayList<>();
        log.info(AnsiColors.CYAN + "\n=== DOWNLOADING %d IMAGES ===".formatted(webImageList.size()) + AnsiColors.RESET);

        try {
            Path dir = Files.createDirectories(Paths.get(downloadFolder));
            for (int i = 0; i < webImageList.size(); i++) {
                WebImage image = webImageList.get(i);
                log.info(AnsiColors.CYAN + String.format(
                        "%d/%d: %s",
                        i + 1, webImageList.size(), image.getDirectLink()
                ) + AnsiColors.RESET);
                processImage(image, dir, files);
            }
        } catch (IOException e) {
            log.severe(AnsiColors.RED + "Download error: " + e.getMessage() + AnsiColors.RESET);
        }
        return files;
    }



    private void processImage(WebImage image, Path dir, List<File> files) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                URI uri = new URI(image.getDirectLink());
                Path outputPath = dir.resolve(sanitizeFileName(uri.getPath()));

                if (Files.notExists(outputPath)) {
                    downloadFile(uri, outputPath);
                    log.info(AnsiColors.GREEN + String.format(
                            "Downloaded: %s (Attempt %d)",
                            outputPath.getFileName(), attempt
                    ) + AnsiColors.RESET);
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
            Thread.sleep(minDelayMs + random.nextInt(maxDelayMs - minDelayMs));
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
        String message = String.format(
                "Attempt %d/%d failed: %s | URL: %s",
                attempt, maxRetries, e.getMessage(), image.getDirectLink()
        );

        if (attempt == maxRetries) {
            log.severe(AnsiColors.RED + message + AnsiColors.RESET);
        } else {
            log.warning(AnsiColors.YELLOW + message + AnsiColors.RESET);
        }
    }
}