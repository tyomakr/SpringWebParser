package ru.aikr.inet.parser.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.HtmlParserService;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.UserAgentService;
import ru.aikr.inet.parser.service.WebImageParserService;
import ru.aikr.inet.parser.util.AnsiColors;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FishkiWebImageParserService implements WebImageParserService {

    private static final Logger log = Logger.getLogger("FishkiParserService");
    private final ImageDownloaderService imageDownloader;
    private final UserAgentService userAgentService;
    private final HtmlParserService htmlParser;
    private final SecurityConfigService securityConfig;
    private final DelayService delayService;

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


    // Инициализация
    @PostConstruct
    public void init() {
        log.info(AnsiColors.CYAN + "=".repeat(50) + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + "Fishki Parser initialized" + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + String.format(
                "Config: URL=%s | Selector=%s", fishkiUrl, divContainerWithImage
        ) + AnsiColors.RESET);
        log.info(AnsiColors.CYAN + "=".repeat(50) + AnsiColors.RESET);
    }


    @Override
    public List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd) {
        List<WebImage> resultList = new ArrayList<>();
        log.info(AnsiColors.CYAN + "\n=== PARSING PAGES %d-%d ===".formatted(pageBegin, pageEnd) + AnsiColors.RESET);

        for (int i = pageBegin; i <= pageEnd; i++) {
            log.info(AnsiColors.CYAN + "-".repeat(40) + AnsiColors.RESET);
            parsePage(i, resultList);
            delayService.humanDelay();
        }
        return resultList;
    }


    private void parsePage(int pageNumber, List<WebImage> resultList) {
        String url = fishkiUrl + pageNumber;
        String ua = userAgentService.getRandomUserAgent();

        log.info(AnsiColors.CYAN + String.format(
                "Parsing page %d | URL: %s | User-Agent: %s",
                pageNumber, url, ua.substring(0, Math.min(40, ua.length())) + "..." + AnsiColors.RESET
        ));

        try {
            SSLContext sslContext = securityConfig.getUnsafeSSLContext();
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
            resultList.addAll(htmlParser.parsePage(url, divContainerWithImage));

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


    // Загрузка изображений с повторами
    @Override
    public List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList) {
        return imageDownloader.downloadImages(webImageList, Path.of(downloadFolder))
                .stream()
                .map(Path::toFile)
                .collect(Collectors.toList());
    }


    private Map<String, String> getBrowserHeaders() {
        return Map.of(
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
                "Cache-Control", "no-cache"
        );
    }



}