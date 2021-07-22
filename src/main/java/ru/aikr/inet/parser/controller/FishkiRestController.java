package ru.aikr.inet.parser.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sites/fishki")
public class FishkiRestController {

    private final WebImageParserService parser;
    private final VKPublishService vkPublishService;

    //получение картинок от сайта из определенного диапазона страниц и замещение новым запросом старой коллекции в базе
    @GetMapping("/images/{num1}/to/{num2}")
    public ResponseEntity<List<WebImage>> getImagesFromPages(@PathVariable("num1") String pageBegin,
                                                             @PathVariable("num2") String pageEnd) {

        List<WebImage> linksImagesList =
                parser.getImageLinksFromPages(Integer.parseInt(pageBegin), Integer.parseInt(pageEnd));
        return ResponseEntity
                .ok()
                .body(linksImagesList);
    }

    //получение списка выбранных картинок из фронтэнда и отправка на публикацию
    @PostMapping("/images")
    public ResponseEntity<List<WebImage>> saveAndPublishSelectedImages(@RequestBody List<WebImage> webImageList) {
        vkPublishService.postToWall(webImageList);
        return ResponseEntity.ok().build();
    }
}
