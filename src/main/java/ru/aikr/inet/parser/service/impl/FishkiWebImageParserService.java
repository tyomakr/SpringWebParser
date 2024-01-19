package ru.aikr.inet.parser.service.impl;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    @Value("${env.parser.download-folder-name}")
    private String downloadFolder;


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

        try {
            Path dir = Files.createDirectories(Paths.get(downloadFolder));

            for (WebImage webImage : webImageList) {
                URL currentUrl = new URL(webImage.getDirectLink());
                File downloadableFilePath = new File(dir + "\\" + currentUrl.getFile().replaceAll("/", ""));

                if (!downloadableFilePath.exists()) {
                    downloadFile(currentUrl, downloadableFilePath.getPath());
                    log.info("Download file: " + downloadableFilePath.getName() + " : " + downloadableFilePath.length()/1024 + " Kb");
                } else {
                    log.info("File " + downloadableFilePath.getName() + " already downloaded. Skipped"); // на случай внезапного прерывания программы и ошибок во взаимодействии с api vk
                }
                fileList.add(downloadableFilePath);
            }

        } catch (IOException e) {
            log.warning("ERROR: " + e.getMessage());
        }

        return fileList;
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

    private void getImgLinksPerPage(int numberOfPage, List<WebImage> resultList) {

        String currentParseUrl = fishkiUrl + numberOfPage;

        try {
            Document doc = Jsoup.connect(currentParseUrl).get();
            List<Element> elements = doc.select(divContainerWithImage);

            for (Element element : elements) {
                resultList.add(new WebImage(element.children().attr("abs:href")));
            }

        } catch (IOException e) {
            log.warning(e.getMessage());
        }

    }
}
