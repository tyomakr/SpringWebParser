package ru.aikr.inet.parser.controller;

import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sites/fishki")
@CrossOrigin(origins = "http://localhost:3333")
public class FishkiRestController {

    private final WebImageService webImageService;
    private final VKPublishService vkPublishService;

    public FishkiRestController(WebImageService webImageService,
                                VKPublishService vkPublishService) {
        this.webImageService = webImageService;
        this.vkPublishService = vkPublishService;
    }

    /** Возвращает Flux картинок с fishki.net вместо List */
    @GetMapping("/images/{num1}/to/{num2}")
    public Flux<WebImage> getImagesFromPages(@PathVariable int num1,
                                             @PathVariable int num2) {
        return webImageService.getImagesFromPages(num1, num2);
    }

    /** Сохраняет и публикует выбранные на ВКонтакте реактивно */
    @PostMapping(path = {"/images", "/images/"})
    public Mono<ResponseEntity<String>> saveAndPublish(@RequestBody List<WebImage> images) {
        return vkPublishService.generatePostsAndPublishToCommunityWall(images)
                .map(count -> ResponseEntity.ok("Опубликовано " + count + " изображений"))
                .defaultIfEmpty(ResponseEntity.status(500).body("Ошибка публикации"));
    }
}