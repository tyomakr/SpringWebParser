package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;


@Service
@RequiredArgsConstructor
public class FishkiWebImageParserService implements WebImageParserService {

    private static final Logger log = Logger.getLogger("FishkiParserService");

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

        log.info("Received successfully " + resultList.size() + " links");

        return resultList;
    }

    @Override
    public List<File> downloadImagesFromWebImageLinks(List<WebImage> webImageList) {

        List<File> fileList = new ArrayList<>();
        //excludingWebpImages(webImageList);

        for(WebImage webImage : webImageList) {
            try {
                URL currentUrl = new URL(webImage.getDirectLink());
                String ext = getFileExtension(webImage.getDirectLink());
                File file = new File(System.currentTimeMillis() + "." + ext);
                downloadFile(currentUrl, file.getName());
                log.info("Download file: " + file.getName() + " : " + file.length() + " bytes");
                fileList.add(file);

            } catch (Exception e) {
                e.printStackTrace();
                log.warning("ERROR: " + e.getMessage());
            }
        }
        return fileList;
    }

    private String getFileExtension(String filePath) {
        int extensionPos = filePath.lastIndexOf(".") + 1;
        return filePath.substring(extensionPos);
    }

    private static void downloadFile(URL url, String outputFileName) {

        try (InputStream in = url.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(outputFileName)) {

            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            log.warning("ERROR: " + e.getMessage());
        }
    }

    private List<WebImage> excludingWebpImages (List<WebImage> webImageList) {
        //проверка на наличие проблемных расширений (типа webp)
        Iterator<WebImage> imageIterator = webImageList.iterator();
        while (imageIterator.hasNext()) {
            WebImage nextWebImage = imageIterator.next();
            if (nextWebImage.getDirectLink().matches("^.*webp$")) {
                log.warning("Excluding (webp ext) : " + nextWebImage.getDirectLink());
                imageIterator.remove();
            }
        }
        return webImageList;
    }

    private List<WebImage> getImgLinksPerPage(int numberOfPage, List<WebImage> resultList) {

        String currentParseUrl = fishkiUrl + numberOfPage;

        try {
            Document doc = Jsoup.connect(currentParseUrl).get();
            List<Element> elements = doc.select(divContainerWithImage);

            for (Element element : elements) {
                resultList.add(new WebImage(element.children().attr("abs:href")));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultList;
    }
}
