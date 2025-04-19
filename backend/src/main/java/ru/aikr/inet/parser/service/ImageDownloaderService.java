package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.model.WebImage;
import java.nio.file.Path;
import java.util.List;

public interface ImageDownloaderService {
    List<Path> downloadImages(List<WebImage> images, Path outputDir);
}