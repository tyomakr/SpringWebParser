package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.config.SecurityConfig;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.HtmlParserService;
import ru.aikr.inet.parser.util.AnsiColors;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HtmlParserServiceImpl implements HtmlParserService {
    private static final Logger log = Logger.getLogger("HtmlParserService");
    private final SecurityConfig securityConfig;

    @Override
    public List<WebImage> parsePage(Connection.Response response, String cssSelector) throws IOException {
        log.info(AnsiColors.CYAN + "Parsing URL: " + response.url() + AnsiColors.RESET);

        try {
            Document doc = Jsoup.connect(String.valueOf(response.url()))
                    .sslSocketFactory(securityConfig.getUnsafeSSLContext().getSocketFactory())
                    .get();

            return doc.select(cssSelector).stream()
                    .map(this::extractWebImage)
                    .collect(Collectors.toList());

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.severe(AnsiColors.RED + "SSL Error: " + e.getMessage() + AnsiColors.RESET);
            throw new IOException("SSL configuration failed", e);
        }
    }

    private WebImage extractWebImage(Element element) {
        String link = element.children().attr("abs:href");
        return new WebImage(link);
    }
}
