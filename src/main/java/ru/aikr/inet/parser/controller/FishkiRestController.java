package ru.aikr.inet.parser.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sites/fishki")
public class FishkiRestController {

    private final WebImageService webImageService;
    private final VKPublishService vkPublishService;

    public FishkiRestController(WebImageService webImageService, VKPublishService vkPublishService) {
        this.webImageService = webImageService;
        this.vkPublishService = vkPublishService;
    }

    //получение картинок от сайта из определенного диапазона страниц и замещение новым запросом старой коллекции в базе
    @GetMapping("/images/{num1}/to/{num2}")
    public ResponseEntity<List<WebImage>> getImagesFromPages(@PathVariable("num1") int startPage,
                                                             @PathVariable("num2") int endPage) {

        List<WebImage> images = webImageService.getImagesFromPages(startPage, endPage);
        return ResponseEntity.ok(images);
    }

    //получение списка выбранных картинок из фронтэнда и отправка на публикацию
    @PostMapping(value = "/images/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveAndPublishSelectedImages(@RequestBody List<WebImage> webImageList) {
        boolean result = vkPublishService.generatePostsAndPublishToCommunityWall(webImageList);
        if (result) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"))
                    .body("Опубликовано " + webImageList.size() + " изображений");
        }
        return ResponseEntity.badRequest()
                .body("Failed to publish images");
    }
}
