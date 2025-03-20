package ru.aikr.inet.parser.repository.impl;

import jakarta.annotation.PostConstruct;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import ru.aikr.inet.parser.exception.ErrorProcessor;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.network.ConnectionConfigurator;
import ru.aikr.inet.parser.proxy.ProxyHandler;
import ru.aikr.inet.parser.repository.WebImageRepository;
import ru.aikr.inet.parser.service.HtmlParserService;
import ru.aikr.inet.parser.service.impl.DelayService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class FishkiWebImageRepositoryImpl implements WebImageRepository {

    private static final Logger log = Logger.getLogger("Fishki Parser Initialized");

    @Qualifier("fishkiConnectionConfigurator")
    private final ConnectionConfigurator connectionConfigurator;
    private final HtmlParserService htmlParser;
    private final ProxyHandler proxyHandler;
    private final ErrorProcessor errorProcessor;
    private final DelayService delayService;

    @Value("${sites.fishki.fishki-url}")
    private String fishkiUrl;

    @Value("${sites.fishki.fishki-div-container-with-image}")
    private String divContainerWithImage;


    @Autowired
    public FishkiWebImageRepositoryImpl(
            @Qualifier("fishkiConnectionConfigurator") ConnectionConfigurator connectionConfigurator,
            HtmlParserService htmlParser,
            ProxyHandler proxyHandler,
            ErrorProcessor errorProcessor,
            DelayService delayService) {
        this.connectionConfigurator = connectionConfigurator;
        this.htmlParser = htmlParser;
        this.proxyHandler = proxyHandler;
        this.errorProcessor = errorProcessor;
        this.delayService = delayService;
    }


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
    public List<WebImage> findImagesByPageRange(int pageBegin, int pageEnd) {
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

        try {
            Connection.Response response = connectionConfigurator.configureConnection(url);
            String userAgent = connectionConfigurator.getLastUserAgent();

            log.info(AnsiColors.CYAN + String.format(
                    "Parsing page %d | URL: %s | User-Agent: %s",
                    pageNumber, url, userAgent.substring(0, Math.min(40, userAgent.length())) + "..."
            ) + AnsiColors.RESET);

            log.info(AnsiColors.CYAN + String.format(
                    "Response: %d %s",
                    response.statusCode(), response.statusMessage()
            ) + AnsiColors.RESET);

            if (response.hasHeader("Location")) {
                String redirectUrl = response.header("Location");
                log.warning(AnsiColors.YELLOW + String.format(
                        "Redirect detected → %s", redirectUrl
                ) + AnsiColors.RESET);
                int redirectedPageNumber = extractPageNumber(redirectUrl);
                parsePage(redirectedPageNumber, resultList);
                return;
            }

            List<WebImage> parsedImages = htmlParser.parsePage(response, divContainerWithImage);
            resultList.addAll(parsedImages);

            if (resultList.isEmpty()) {
                log.warning(AnsiColors.YELLOW + String.format(
                        "No images found! Check selector: %s", divContainerWithImage
                ) + AnsiColors.RESET);
            } else {
                log.info(AnsiColors.GREEN + String.format(
                        "Found %d images", resultList.size()
                ) + AnsiColors.RESET);
            }

        } catch (Exception e) {
            errorProcessor.handlePageError(pageNumber, url, e);
            proxyHandler.retryWithProxy(url);
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


}
