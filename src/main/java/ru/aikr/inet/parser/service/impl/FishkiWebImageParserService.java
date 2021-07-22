package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FishkiWebImageParserService implements WebImageParserService {

    @Value("${sites.fishki-url}")
    private String fishkiUrl;

    @Value("${sites.fishki-div-container-with-image}")
    private String divContainerWithImage;


    @Override
    public List<WebImage> getImageLinksFromPages(int pageBegin, int pageEnd) {
        List<WebImage> resultList = new ArrayList<>();

        for (int i = pageBegin; i <= pageEnd; i++) {
            getImgLinksPerPage(i, resultList);
        }

        return resultList;
    }

    private List<WebImage> getImgLinksPerPage(int numberOfPage, List<WebImage> resultList) {

        String currentParseUrl = fishkiUrl + numberOfPage;

        try {
            Document doc = Jsoup.connect(currentParseUrl).get();
            List<Element> elements = doc.select(divContainerWithImage);

            for (Element element : elements) {
                resultList.add(new WebImage(element.children().attr("abs:href")));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return resultList;
    }
}
