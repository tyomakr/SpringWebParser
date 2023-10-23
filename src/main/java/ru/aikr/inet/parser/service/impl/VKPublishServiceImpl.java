package ru.aikr.inet.parser.service.impl;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.domain.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageParserService;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

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
    @Value("${vk.time-post-pub-delay}")
    private Integer POST_PUB_DELAY_TIME;


    @Override
    public boolean postToWall(List<WebImage> fullImagesList) {

        boolean isSuccess = true;

        try {
            //get UserActor
            UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

            log.info("BEGIN SENDING TO VK");

            //чистим дубликаты изображений (реализация со стороны бэкэнда)
            Set<WebImage> uniqueList = new HashSet<>(fullImagesList);
            fullImagesList.clear();
            fullImagesList.addAll(uniqueList);


            //преобразуем полный список WebImage в список File, попутно выкачивая файлы
            List<File> fileList = webImageParserService.downloadImagesFromWebImageLinks(fullImagesList);

            //собираем коллекцию в обратном порядке для удобства просмотра связанных изображений
            Collections.reverse(fileList);

            //делим количественно по 10 шт. (на каждый пост)
            List<List<File>> chunkedLists = chunkify(fileList, CHUNK_SIZE);

            //createPost в цикле
            for (List<File> chunkedList : chunkedLists) {
                isSuccess = createPost(userActor, chunkedList);
                if (!isSuccess) {
                    break;
                }
            }

            //удаляем скачанные файлы
            deleteDownloadedFiles(fileList);

            log.info("SENDING FINISHED");

        } catch (Exception e) {
            log.warning("Problem to post: " + e.getMessage());
            isSuccess = false;
        }

        return isSuccess;
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


    private boolean createPost(UserActor actor, List<File> fileList) {

        boolean isSuccess = true;

        TransportClient transportClient = HttpTransportClient.getInstance();
        VkApiClient vk = new VkApiClient(transportClient);

        StringBuilder attachIds = new StringBuilder();

        //для каждого изображения
        try {
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

        } catch (ClientException e) {
            log.warning("WARN\tClientException:\t" + e.getMessage());
            isSuccess = false;
        } catch (InterruptedException e) {
            log.warning("WARN\tInterruptedException:\t" + e.getMessage());
            isSuccess = false;
        } catch (ApiException e) {
            log.warning("WARN\tApiException:\t" + e.getMessage());
            isSuccess = false;
        }



        //del last comma
        attachIds.delete(attachIds.length() - 1, attachIds.length());

        //post to group wall
        try {
            PostResponse postResponse = vk.wall().post(actor)
                    .ownerId(GROUP_ID)
                    .attachments(attachIds.toString())
                    .execute();
            log.info(postResponse.toPrettyString());
        } catch (ApiException e) {
            log.warning("WARN\tApiException:\t" + e.getMessage());
            isSuccess = false;
        } catch (ClientException e) {
            log.warning("WARN\tClientException:\t" + e.getMessage());
            isSuccess = false;
        }

        return isSuccess;
    }

    private void deleteDownloadedFiles(List<File>fileList) {
        log.info("Cleaning downloaded files...");
        for (File file : fileList) {
            FileUtils.deleteQuietly(file);
        }
    }
}