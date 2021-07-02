package ru.aikr.inet.parser.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.repository.WebImageRepository;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sites/fishki")
public class FishkiRestController {

    private final WebImageParserService parser;
    private final WebImageRepository webImageRepository;

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

    //получение всех ссылок на картинки из базы
    @GetMapping("/images/findAll")
    public ResponseEntity<List<WebImage>> findAll(){
        return ResponseEntity.ok(webImageRepository.findAll());
    }
}
