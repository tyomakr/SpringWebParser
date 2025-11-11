package ru.aikr.inet.parser.source.repository.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import ru.aikr.inet.parser.exception.ErrorProcessor;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.network.ConnectionConfigurator;
import ru.aikr.inet.parser.network.proxy.ProxyHandler;
import ru.aikr.inet.parser.source.repository.WebImageRepository;
import ru.aikr.inet.parser.source.service.HtmlParserService;
import ru.aikr.inet.parser.source.fishki.service.impl.DelayService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Repository
public class FishkiWebImageRepositoryImpl implements WebImageRepository {

    private final ConnectionConfigurator connectionConfigurator;
    private final ErrorProcessor errorProcessor;
    private final ProxyHandler proxyHandler;
    private final DelayService delayService;
    private final HtmlParserService htmlParser;        // <<< новинка

    @Value("${sites.fishki.fishki-url}")
    private String fishkiUrl;

    @Value("${sites.fishki.fishki-div-container-with-image}")
    private String divContainerWithImage;

    @Autowired
    public FishkiWebImageRepositoryImpl(
            @Qualifier("fishkiConnectionConfigurator") ConnectionConfigurator connectionConfigurator,
            ErrorProcessor errorProcessor,
            ProxyHandler proxyHandler,
            DelayService delayService,
            HtmlParserService htmlParser           // <<< даём в конструктор
    ) {
        this.connectionConfigurator = connectionConfigurator;
        this.errorProcessor = errorProcessor;
        this.proxyHandler = proxyHandler;
        this.delayService = delayService;
        this.htmlParser = htmlParser;              // <<<
    }

    /** Лог при старте парсера */
    @PostConstruct
    public void init() {
        log.info("{}Fishki Parser initialized | URL={} | Selector={}{}",
                AnsiColors.CYAN, fishkiUrl, divContainerWithImage, AnsiColors.RESET);
    }

    /** Основной метод: парсим страницы [pageBegin..pageEnd] */
    @Override
    public List<WebImage> findImagesByPageRange(int pageBegin, int pageEnd) {
        List<WebImage> resultList = new ArrayList<>();
        log.info("{}PARSING PAGES {}/{}{}", AnsiColors.CYAN, pageBegin, pageEnd, AnsiColors.RESET);

        for (int page = pageBegin; page <= pageEnd; page++) {
            parsePage(page, resultList);
            delayService.humanDelay();      // пауза между страницами
        }
        return resultList;
    }

    /** Парсит одну страницу, вытаскивает <img> из контейнера и логирует. */
    private void parsePage(int pageNumber, List<WebImage> resultList) {
        String url = fishkiUrl + pageNumber;

        try {
            Connection.Response response = connectionConfigurator.configureConnection(url);
            String ua = connectionConfigurator.getLastUserAgent();
            log.info(AnsiColors.CYAN + "Parsing page {} | URL: {} | UA: {}{}",
                    pageNumber, url,
                    ua == null ? "n/a"
                            : ua.substring(0, Math.min(40, ua.length())) + "...",
                    AnsiColors.RESET);

            log.info(AnsiColors.CYAN + "Response: {} {}" + AnsiColors.RESET,
                    response.statusCode(), response.statusMessage());

            // обрабатываем редирект
            if (response.hasHeader("Location")) {
                String redirectUrl = response.header("Location");
                log.warn(AnsiColors.YELLOW + "Redirect detected → {}" + AnsiColors.RESET,
                        redirectUrl);
                int redirectedPage = extractPageNumber(redirectUrl);
                parsePage(redirectedPage, resultList);
                return;
            }

            // *** вот здесь теперь зовём htmlParser ***
            Document doc = htmlParser.parse(response);

            Elements containers = doc.select(divContainerWithImage);
            if (containers.isEmpty()) {
                log.warn(AnsiColors.YELLOW + "No containers found with selector '{}' on page {}"
                        + AnsiColors.RESET, divContainerWithImage, pageNumber);
            }

            List<WebImage> parsed = new ArrayList<>();
            for (var cont : containers) {
                cont.select("img")
                        .forEach(img -> parsed.add(new WebImage(img.attr("src"))));
            }

            if (parsed.isEmpty()) {
                log.warn(AnsiColors.YELLOW + "Found 0 images on page {} — check selector '{}'"
                        + AnsiColors.RESET, pageNumber, divContainerWithImage);
            } else {
                log.info(AnsiColors.GREEN + "Found {} images on page {}" + AnsiColors.RESET,
                        parsed.size(), pageNumber);
                resultList.addAll(parsed);
            }

        } catch (Exception ex) {
            log.error(AnsiColors.RED + "Error parsing page {}: {}" + AnsiColors.RESET,
                    pageNumber, ex.getMessage());

            // обработка ошибок и retry через прокси
            errorProcessor.handlePageError(pageNumber, url, ex);
            proxyHandler.retryWithProxy(url);
        }
    }

    /** Вспомогательный метод для обработки редиректов. */
    private int extractPageNumber(String url) {
        try {
            Matcher m = Pattern.compile("/(\\d+)/?$").matcher(url);
            return m.find() ? Integer.parseInt(m.group(1)) : 1;
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
