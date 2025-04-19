package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.repository.WebImageRepository;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.WebImageService;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebImageServiceImpl implements WebImageService {

    private final WebImageRepository repository;
    private final ImageDownloaderService imageDownloaderService;

    @Value("${env.parser.download-folder-name}")
    private String downloadFolder;

    public List<WebImage> getImagesFromPages(int startPage, int endPage) {
        return repository.findImagesByPageRange(startPage, endPage);
    }

    @Override
    public List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList) {
        return imageDownloaderService.downloadImages(webImageList, Path.of(downloadFolder))
                .stream()
                .map(Path::toFile)
                .collect(Collectors.toList());
    }
}
