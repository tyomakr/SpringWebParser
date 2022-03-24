package ru.aikr.inet.parser.service.impl;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

    private static final TransportClient transportClient = HttpTransportClient.getInstance();
    private static final VkApiClient vk = new VkApiClient(transportClient);
    private static final Logger log = Logger.getLogger("VKPublishService");

    private final WebImageParserService webImageParserService;

    @Value("${vk.user-id}")
    private Integer USER_ID;
    @Value("${vk.group-id}")
    private Integer GROUP_ID;
    @Value("${vk.access-token}")
    private String ACCESS_TOKEN;
    @Value("${vk.chunk-size}")
    private Integer CHUNK_SIZE;
    @Value("${time.post-pub-delay}")
    private Integer POST_PUB_DELAY_TIME;


    @Override
    public boolean postToWall(List<WebImage> fullImagesList) {
        try {
            //get UserActor
            UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

            log.info("BEGIN SENDING TO VK");

            //преобразуем полный список WebImage в список File, попутно выкачивая файлы
            List<File> fileList = webImageParserService.downloadImagesFromWebImageLinks(fullImagesList);

            //собираем коллекцию в обратном порядке для удобства просмотра связанных изображений
            Collections.reverse(fileList);

            //делим количественно по 10 шт. (на каждый пост)
            List<List<File>> chunkedLists = chunkify(fileList, CHUNK_SIZE);

            //createPost в цикле
            for (List<File> chunkedList : chunkedLists) {
                createPost(userActor, chunkedList);
            }

            //удаляем скачанные файлы
            log.info("Cleaning downloaded files...");
            deleteDownloadedFiles(fileList);
            log.info("SENDING FINISHED");

            return true;

        } catch (Exception e) {
            log.warning("Problem to post: " + e.getMessage());
            return false;
        }
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
            Thread.sleep(POST_PUB_DELAY_TIME);
        }

        //del last comma
        attachIds.delete(attachIds.length() - 1, attachIds.length());

        //post to group wall
        PostResponse postResponse = vk.wall().post(actor)
                .ownerId(GROUP_ID)
                .attachments(attachIds.toString())
                .execute();
        log.info(postResponse.toPrettyString());
    }

    private void deleteDownloadedFiles(List<File>fileList) {
        for (File file : fileList) {
            FileUtils.deleteQuietly(file);
        }
    }
}

