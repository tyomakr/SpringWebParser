package ru.aikr.inet.parser.fishki.service;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import java.io.IOException;

/**
 * Преобразует HTTP-ответ в Jsoup-Document.
 * Никакого сетевого I/O — только парсинг HTML.
 */
public interface HtmlParserService {

    Document parse(Connection.Response response) throws IOException;
}
