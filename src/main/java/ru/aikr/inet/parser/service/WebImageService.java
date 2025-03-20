package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.model.WebImage;

import java.io.File;
import java.util.List;

public interface WebImageService {

    List<WebImage> getImagesFromPages(int startPage, int endPage);
    List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList);
}
