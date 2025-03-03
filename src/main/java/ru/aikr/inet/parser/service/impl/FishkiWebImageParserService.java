package ru.aikr.inet.parser.service.impl;

import com.google.gson.*;
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

    private static final int MAX_RETRIES = 5;
    private static final int MIN_DELAY_MS = 1500;
    private static final int MAX_DELAY_MS = 4000;

    private static final Gson gson = new Gson();

    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RESET = "\u001B[0m";

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

    private final Random random = new Random();


    // Инициализация User-Agent при старте
    @PostConstruct
    public void init() {
        loadUserAgents();
    }


    // Загрузка User-Agent из файла
    private void loadUserAgents() {
        try {
            File file = ResourceUtils.getFile(userAgentsFile);
            List<String> agents = Files.readAllLines(file.toPath());
            USER_AGENTS = agents.toArray(new String[0]);
            log.info(ANSI_CYAN + "Loaded " + USER_AGENTS.length + " User-Agents" + ANSI_RESET);
        } catch (IOException e) {
            log.severe(ANSI_RED + "Failed to load User-Agents: " + e.getMessage() + ANSI_RESET);
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
        for (int i = pageBegin; i <= pageEnd; i++) {
            parsePage(i, resultList);
            humanDelay();
        }
        return resultList;
    }


    private void parsePage(int pageNumber, List<WebImage> resultList) {
        String url = fishkiUrl + pageNumber;
        String ua = getRandomUserAgent();

        try {
            // Настройка соединения с явным указанием TLSv1.2
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, getTrustAllCerts(), new SecureRandom());

            Connection connection = Jsoup.connect(url)
                    .userAgent(ua)
                    .sslSocketFactory(sslContext.getSocketFactory())
                    .headers(getBrowserHeaders())
                    .ignoreContentType(true)
                    .timeout(30000)
                    .followRedirects(true)
                    .maxBodySize(0) // Отключаем лимит размера ответа
                    .ignoreHttpErrors(true);

            Connection.Response response = connection.execute();

            // Цветное логирование
            log.info(ANSI_CYAN + String.format(
                    "[Page %d] URL: %s | UA: %s | Status: %d",
                    pageNumber,
                    url,
                    ua.substring(0, Math.min(ua.length(), 40)) + "...",
                    response.statusCode()
            ) + ANSI_RESET);

            // JSON-логирование
            Map<String, Object> logData = new HashMap<>();
            logData.put("page", pageNumber);
            logData.put("url", url);
            logData.put("status", response.statusCode());
            log.info(gson.toJson(logData));

            // Обработка редиректов
            if (response.hasHeader("Location")) {
                String redirectUrl = response.header("Location");
                int redirectPage = extractPageNumber(redirectUrl);
                log.warning(ANSI_YELLOW + String.format(
                        "[Page %d] Redirect to: %s (Page %d)",
                        pageNumber, redirectUrl, redirectPage
                ) + ANSI_RESET);
                parsePage(redirectPage, resultList);
                return;
            }

            if (response.statusCode() == 200) {
                Document doc = response.parse();
                processElements(doc.select(divContainerWithImage), resultList);

                if (resultList.isEmpty()) {
                    log.warning(ANSI_YELLOW + String.format(
                            "[Page %d] No images found. Selector: %s",
                            pageNumber, divContainerWithImage
                    ) + ANSI_RESET);
                }
            } else {
                log.warning(ANSI_YELLOW + String.format(
                        "[Page %d] Unexpected status: %d",
                        pageNumber, response.statusCode()
                ) + ANSI_RESET);
            }

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            log.severe(ANSI_RED + String.format(
                    "[Page %d] Error: %s | URL: %s",
                    pageNumber, e.getMessage(), url
            ) + ANSI_RESET);
            retryWithProxy(url);
        }
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
            log.info(ANSI_CYAN + "Retrying with proxy..." + ANSI_RESET);
            Connection connection = Jsoup.connect(url)
                    .proxy(proxyUrl, Integer.parseInt(proxyPort))
                    .timeout(30000);
            Connection.Response response = connection.execute();
        } catch (IOException e) {
            log.severe(ANSI_RED + "Proxy retry failed: " + e.getMessage() + ANSI_RESET);
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