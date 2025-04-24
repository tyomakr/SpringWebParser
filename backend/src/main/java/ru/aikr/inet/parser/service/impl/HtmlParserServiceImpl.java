package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.aikr.inet.parser.service.HtmlParserService;
import ru.aikr.inet.parser.service.UserAgentService;

import javax.net.ssl.SSLContext;

/**
 * Реактивная реализация HtmlParserService
 * адаптированная под SSLContext-бин и UserAgentService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlParserServiceImpl implements HtmlParserService {

    private final SSLContext sslContext;
    private final UserAgentService userAgentService;

    @Value("${default.headers.accept}")
    private String acceptHeader;
    @Value("${default.headers.accept-language}")
    private String acceptLanguageHeader;
    @Value("${default.headers.cache-control}")
    private String cacheControlHeader;
    @Value("${default.headers.connection.timeout}")
    private int timeout;

    @Override
    public Mono<Document> parseUrl(String url) {
        return Mono.fromCallable(() ->
                        Jsoup.connect(url)
                                .userAgent(userAgentService.getRandomUserAgent())
                                .sslSocketFactory(sslContext.getSocketFactory())
                                .header("Accept", acceptHeader)
                                .header("Accept-Language", acceptLanguageHeader)
                                .header("Cache-Control", cacheControlHeader)
                                .timeout(timeout)
                                .ignoreContentType(true)
                                .get()
                )
                .subscribeOn(Schedulers.boundedElastic());
    }
}