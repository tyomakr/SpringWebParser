package ru.aikr.inet.parser.fishki.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.fishki.service.HtmlParserService;
import ru.aikr.inet.parser.util.AnsiColors;

import java.io.IOException;

@Slf4j
@Service
public class HtmlParserServiceImpl implements HtmlParserService {

    @Override
    public Document parse(Connection.Response response) throws IOException {
        log.info("{}Parsing URL: {}{}", AnsiColors.CYAN, response.url(), AnsiColors.RESET);
        return response.parse();
    }
}
