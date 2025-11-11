package ru.aikr.inet.parser.source.repository;

import ru.aikr.inet.parser.model.WebImage;

import java.util.List;


public interface WebImageRepository {
    List<WebImage> findImagesByPageRange(int startPage, int endPage);
}
