package ru.aikr.inet.parser.service;

import org.jsoup.Connection;
import ru.aikr.inet.parser.model.WebImage;
import java.io.IOException;
import java.util.List;

public interface HtmlParserService {

    List<WebImage> parsePage(Connection.Response response, String cssSelector) throws IOException;
}