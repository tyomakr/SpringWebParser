package ru.aikr.inet.parser.network.impl;

import lombok.Getter;
import org.jsoup.Connection.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.config.SecurityConfig;
import ru.aikr.inet.parser.service.UserAgentService;

import java.io.IOException;
import java.util.Map;

@Component
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

    @Autowired
    public FishkiConnectionConfigurator(SecurityConfig securityConfig, UserAgentService userAgentService) {
        super(securityConfig, userAgentService);
    }


    @Override
    public Response configureConnection(String url) throws IOException {
        return super.configureConnection(url);
    }

    @Override
    public String getLastUserAgent() {
        return super.getLastUserAgent();
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
