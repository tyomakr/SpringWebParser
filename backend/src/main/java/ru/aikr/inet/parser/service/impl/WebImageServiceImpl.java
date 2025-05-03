package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.repository.WebImageRepository;
import ru.aikr.inet.parser.service.ImageDownloaderService;
import ru.aikr.inet.parser.service.WebImageService;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebImageServiceImpl implements WebImageService {

    private final WebImageRepository     repository;
    private final ImageDownloaderService downloaderService;

    /**
     * Делегируем парсинг (с логами и паузами) в репозиторий.
     */
    @Override
    public Flux<WebImage> getImagesFromPages(int startPage, int endPage) {
        return Mono.fromCallable(() ->
                        repository.findImagesByPageRange(startPage, endPage)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Делегируем скачивание картинок в ImageDownloaderService,
     * который знает про maxRetries, папки и логирование.
     */
    @Override
    public Mono<List<Path>> downloadImagesFromWebImageLinks(
            List<WebImage> webImageList,
            Path targetDir
    ) {
        return downloaderService.downloadImages(webImageList, targetDir)
                .subscribeOn(Schedulers.boundedElastic());
    }
}