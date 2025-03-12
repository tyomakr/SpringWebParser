package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.domain.WebImage;
import java.io.IOException;
import java.util.List;

public interface HtmlParserService {
    List<WebImage> parsePage(String url, String cssSelector) throws IOException;
}