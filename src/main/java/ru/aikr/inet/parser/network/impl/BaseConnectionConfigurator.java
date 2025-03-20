package ru.aikr.inet.parser.network.impl;

import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.config.SecurityConfig;
import ru.aikr.inet.parser.network.ConnectionConfigurator;
import ru.aikr.inet.parser.service.UserAgentService;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Component
public class BaseConnectionConfigurator implements ConnectionConfigurator {

    private final SecurityConfig securityConfig;
    private final UserAgentService userAgentService;
    @Getter
    private String lastUserAgent;

    @Value("${default.headers.accept}")
    private String acceptHeader;

    @Value("${default.headers.accept-language}")
    private String acceptLanguageHeader;

    @Value("${default.headers.cache-control}")
    private String cacheControlHeader;

    @Getter
    @Value("${default.headers.connection.timeout}")
    private int timeout;


    @Autowired
    public BaseConnectionConfigurator(SecurityConfig securityConfig, UserAgentService userAgentService) {
        this.securityConfig = securityConfig;
        this.userAgentService = userAgentService;
    }


    @Override
    public Connection.Response configureConnection(String url) throws IOException {

        SSLContext sslContext;
        try {
            sslContext = securityConfig.getUnsafeSSLContext();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IOException("Failed to initialize SSL context", e);
        }
        String userAgent = userAgentService.getRandomUserAgent();
        lastUserAgent = userAgent;

        return Jsoup.connect(url)
                .userAgent(userAgent)
                .sslSocketFactory(sslContext.getSocketFactory())
                .headers(getDefaultHeaders())
                .ignoreContentType(true)
                .timeout(timeout)
                .followRedirects(true)
                .execute();
    }

    protected Map<String, String> getDefaultHeaders() {
        return Map.of(
                "Accept", acceptHeader,
                "Accept-Language", acceptLanguageHeader,
                "Cache-Control", cacheControlHeader
        );
    }
}
