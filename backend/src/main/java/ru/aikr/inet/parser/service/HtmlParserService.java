package ru.aikr.inet.parser.service;

import org.jsoup.nodes.Document;
import reactor.core.publisher.Mono;

/**
 * Сервис для реактивного парсинга HTML.
 */
public interface HtmlParserService {
    /**
     * Парсит переданный URL и возвращает Mono<Document>.
     */
    Mono<Document> parseUrl(String url);
}
