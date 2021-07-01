package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.domain.WebImage;

import java.util.List;

public interface WebImageParserService {

    List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd);
}
