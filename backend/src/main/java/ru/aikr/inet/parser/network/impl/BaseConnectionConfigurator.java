package ru.aikr.inet.parser.network.impl;

import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.network.ConnectionConfigurator;
import ru.aikr.inet.parser.fishki.service.UserAgentService;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Map;

/**
 * Реализация ConnectionConfigurator:
 * конфигурирует Jsoup-соединение с SSLContext и динамическими заголовками.
 */
@Component
public class BaseConnectionConfigurator implements ConnectionConfigurator {

    private final SSLContext sslContext;
    private final UserAgentService userAgentService;

    @Getter private String lastUserAgent;

    @Value("${default.headers.accept}")
    private String acceptHeader;
    @Value("${default.headers.accept-language}")
    private String acceptLanguageHeader;
    @Value("${default.headers.cache-control}")
    private String cacheControlHeader;
    @Getter @Value("${default.headers.connection.timeout}")
    private int timeout;

    public BaseConnectionConfigurator(SSLContext sslContext,
                                      UserAgentService userAgentService) {
        this.sslContext = sslContext;
        this.userAgentService = userAgentService;
    }

    @Override
    public Connection.Response configureConnection(String url) throws IOException {
        String ua = userAgentService.getRandomUserAgent();
        lastUserAgent = ua;

        return Jsoup.connect(url)
                .userAgent(ua)
                .sslSocketFactory(sslContext.getSocketFactory())
                .headers(getDefaultHeaders())
                .ignoreContentType(true)
                .timeout(timeout)
                .followRedirects(true)
                .execute();
    }

    /**
     * Базовые заголовки. Может быть переопределено в подклассах.
     */
    protected Map<String, String> getDefaultHeaders() {
        return Map.of(
                "Accept", acceptHeader,
                "Accept-Language", acceptLanguageHeader,
                "Cache-Control", cacheControlHeader
        );
    }
}
