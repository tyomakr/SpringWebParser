package ru.aikr.inet.parser.service.impl;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

    private static final TransportClient transportClient = HttpTransportClient.getInstance();
    private static final VkApiClient vk = new VkApiClient(transportClient);
    private static final Logger log = Logger.getLogger("VKPublishService");

    @Value("${vk.user-id}")
    private Integer USER_ID;
    @Value("${vk.group-id}")
    private Integer GROUP_ID;
    @Value("${vk.access-token}")
    private String ACCESS_TOKEN;
    @Value("${vk.chunk-size}")
    private Integer CHUNK_SIZE;


    @Override
    public boolean postToWall(List<WebImage> fullImagesList) {
        try {
            //get UserActor
            UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

            log.info("BEGIN SENDING TO VK");

            //преобразуем полный список WebImage в список File, попутно выкачивая файлы
            List<File> fileList = convertWebImageListToFileList(fullImagesList);

            //собираем коллекцию в обратном порядке для удобства просмотра связанных изображений
            Collections.reverse(fileList);

            //делим количественно по 10 шт. (на каждый пост)
            List<List<File>> chunkedLists = chunkify(fileList, CHUNK_SIZE);

            //createPost в цикле
            for (List<File> chunkedList : chunkedLists) {
                createPost(userActor, chunkedList);
            }

            //удаляем скачанные файлы
            deleteDownloadedFiles(fileList);
            log.info("SENDING FINISHED");

            return true;

        } catch (Exception e) {
            return false;
        }
    }


    private static List<File> convertWebImageListToFileList(List<WebImage> webImageList) {
        List<File> fileList = new ArrayList<>();

        //проверка на наличие проблемных расширений (типа webp)
        Iterator<WebImage> imageIterator = webImageList.iterator();
        while (imageIterator.hasNext()) {
            WebImage nextWebImage = imageIterator.next();
            if (nextWebImage.getDirectLink().matches("^.*webp$")) {
                log.warning("Excluding (webp ext) : " + nextWebImage.getDirectLink());
                imageIterator.remove();
            }
        }


        try {
            for (WebImage webImage : webImageList) {
                URL currentURL = new URL(webImage.getDirectLink());
                //тут переработать механизм конвертации и можно попробовать добавлять webp
                // без предварительной фильтрации
                BufferedImage img = ImageIO.read(currentURL);
                File file = new File("file" + FilenameUtils.getName(currentURL.getPath()));
                ImageIO.write(img, "jpg", file);
                fileList.add(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.warning("ERROR: " + e.getMessage());
        }

        return fileList;
    }


    //делим большой список на части
    private static <T> List<List<T>> chunkify(List<T> list, int chunkSize){
        List<List<T>> chunks = new ArrayList<>();

        for (int i = 0; i < list.size(); i += chunkSize) {
            List<T> chunk = new ArrayList<>(list.subList(i, Math.min(list.size(), i + chunkSize)));
            chunks.add(chunk);
        }
        return chunks;
    }

    @SneakyThrows
    private void createPost(UserActor actor, List<File> fileList) {

        StringBuilder attachIds = new StringBuilder();
        //для каждого изображения
        for (File file : fileList) {
            GetWallUploadServerResponse serverResponse = vk.photos().getWallUploadServer(actor).execute();

            WallUploadResponse uploadResponse = vk.upload()
                    .photoWall(String.valueOf(serverResponse.getUploadUrl()), file)
                    .execute();

            List<SaveWallPhotoResponse> photoList = vk.photos()
                    .saveWallPhoto(actor, uploadResponse.getPhoto())
                    .server(uploadResponse.getServer())
                    .hash(uploadResponse.getHash())
                    .execute();

            SaveWallPhotoResponse photo = photoList.get(0);
            String attachId = "photo" + photo.getOwnerId() + "_" + photo.getId();

            attachIds.append(attachId).append(",");
            Thread.sleep(500);
        }

        //del last comma
        attachIds.delete(attachIds.length() - 1, attachIds.length());

        //post to group wall
        vk.wall().post(actor)
                .ownerId(GROUP_ID)
                .attachments(attachIds.toString())
                .execute();
    }

    private void deleteDownloadedFiles(List<File>fileList) {
        for (File file : fileList) {
            FileUtils.deleteQuietly(file);
        }
    }
}

