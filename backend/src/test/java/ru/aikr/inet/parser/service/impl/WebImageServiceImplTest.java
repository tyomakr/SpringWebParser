package ru.aikr.inet.parser.service.impl;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.repository.WebImageRepository;
import ru.aikr.inet.parser.service.ImageDownloaderService;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebImageServiceImplTest {

    private final WebImageRepository repository          = mock(WebImageRepository.class);
    private final ImageDownloaderService downloader      = mock(ImageDownloaderService.class);
    private final WebImageServiceImpl service            = new WebImageServiceImpl(repository, downloader);

    @Test
    void getImagesFromPages() {

        List<WebImage> stub = List.of(
                new WebImage("url-1"),
                new WebImage("url-2"));

        when(repository.findImagesByPageRange(1, 3)).thenReturn(stub);

        StepVerifier.create(service.getImagesFromPages(1, 3))
                .expectNextSequence(stub)
                .verifyComplete();

        verify(repository).findImagesByPageRange(1, 3);
    }

    @Test
    void shouldDownloadImages() {
        List<WebImage> imgs = List.of(new WebImage("https://ex/img1.jpg"));
        Path dir           = Path.of("tmp");

        /* подготавливаем заглушку результата */
        List<Path> stubDownloaded = List.of(dir.resolve("img1.jpg"));

        /* мокаем service-download */
        when(downloader.downloadImages(eq(imgs), eq(dir)))
                .thenReturn(Mono.just(stubDownloaded));

        /* проверяем сервис */
        StepVerifier.create(service.downloadImagesFromWebImageLinks(imgs, dir))
                .expectNext(stubDownloaded)
                .verifyComplete();

        verify(downloader).downloadImages(eq(imgs), eq(dir));
    }
}
