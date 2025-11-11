package ru.aikr.inet.parser.network.impl;

import lombok.Getter;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.fishki.service.UserAgentService;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Map;

/**
 * Специализация BaseConnectionConfigurator для fishki.net:
 * подставляет свои заголовки и таймаут.
 */
@Component("fishkiConnectionConfigurator")
public class FishkiConnectionConfigurator extends BaseConnectionConfigurator {

    @Value("${sites.fishki.headers.accept}")
    private String fishkiAcceptHeader;

    @Value("${sites.fishki.headers.accept-language}")
    private String fishkiAcceptLanguageHeader;

    @Value("${sites.fishki.headers.cache-control}")
    private String fishkiCacheControlHeader;

    @Getter
    @Value("${sites.fishki.headers.connection.timeout}")
    private int fishkiTimeout;

    public FishkiConnectionConfigurator(SSLContext sslContext,
                                        UserAgentService userAgentService) {
        super(sslContext, userAgentService);
    }

    @Override
    public Connection.Response configureConnection(String url) throws IOException {
        return super.configureConnection(url);
    }

    @Override
    protected Map<String, String> getDefaultHeaders() {
        return Map.of(
                "Accept", fishkiAcceptHeader,
                "Accept-Language", fishkiAcceptLanguageHeader,
                "Cache-Control", fishkiCacheControlHeader
        );
    }
}
