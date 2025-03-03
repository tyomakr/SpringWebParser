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
    private Long USER_ID;
    @Value("${vk.group-id}")
    private Long GROUP_ID;
    @Value("${vk.access-token}")
    private String ACCESS_TOKEN;
    @Value("${env.vk-publisher.chunk-size}")
    private Integer CHUNK_SIZE;
    @Value("${env.vk-publisher.time-post-pub-delay}")
    private Integer POST_PUB_DELAY_TIME;


    @Override
    public boolean generatePostsAndPublishToCommunityWall(List<WebImage> fullImagesList) {

        boolean isAllPublishSuccess = true;

        UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

        log.info("BEGIN POSTING TO VK");

        log.info("Duplicate check...");
        removeDuplicates(fullImagesList);

        log.info("Downloading images...");
        //преобразуем полный список WebImage в список File, попутно выкачивая файлы
        List<File> fileList = webImageParserService.downloadImagesFromWebImageLinks(fullImagesList);

        log.info("Chunkify...");
        //делим по 10 изображений на каждый пост
        List<List<File>> chunkedLists = chunkify(fileList, CHUNK_SIZE);

        //формируем посты
        for (int currentPost = 0; currentPost < chunkedLists.size(); currentPost++) {

            log.info("Chunk #: " + currentPost);
            try {
                createPost(userActor, chunkedLists.get(currentPost));

            } catch (RuntimeException e) {
                log.warning("Error #4: " + e.getMessage());
                isAllPublishSuccess = false;
                currentPost--;
            }
        }

        //удаляем скачанные файлы
        deleteDownloadedFiles(fileList);
        log.info("POSTING FINISHED");

        return isAllPublishSuccess;

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

        boolean isPostPublishSuccess = false;
        TransportClient transportClient = HttpTransportClient.getInstance();
        VkApiClient vk = new VkApiClient(transportClient);

        StringBuilder attachIds = new StringBuilder();

        log.info("Upload images for post to VK...");

        //для каждого изображения в текущем посте
        for (int currentFileIndex = 0; currentFileIndex < fileList.size(); currentFileIndex++) {

            try {
                GetWallUploadServerResponse serverResponse = vk.photos().getWallUploadServer(actor).execute();

                WallUploadResponse uploadResponse = vk.upload()
                        .photoWall(String.valueOf(serverResponse.getUploadUrl()), fileList.get(currentFileIndex))
                        .execute();

                List<SaveWallPhotoResponse> photoList = vk.photos()
                        .saveWallPhoto(actor, uploadResponse.getPhoto())
                        .server(uploadResponse.getServer())
                        .hash(uploadResponse.getHash())
                        .execute();

                SaveWallPhotoResponse photo = photoList.get(0);
                String attachId = "photo" + photo.getOwnerId() + "_" + photo.getId();

                attachIds.append(attachId).append(",");
                log.info("Image " + photo.getId() + " uploaded. Delay: " + POST_PUB_DELAY_TIME + " ms");

                //пауза для того, чтобы VK API меньше ругался)
                Thread.sleep(POST_PUB_DELAY_TIME);


            } catch (ApiException | ClientException | InterruptedException | ClassCastException e) {
                //сюда падают ошибки при загрузке файла в пост
                log.warning("ERROR #2 : file processing ended with an error : " + e.getMessage());
                log.warning("Retrying processing current file...");
                //если сюда что-то упало, то пытаемся обработать этот файл снова
                currentFileIndex--;
            }
        }

        //удаление последней запятой,и преобразование StringBuilder в String
        String attachedPhotosId = attachIds.delete(attachIds.length() - 1, attachIds.length()).toString();

        //попытка публикации текущего поста
        isPostPublishSuccess = publishCurrentPost(attachedPhotosId, vk, actor);

        //если все ок, то отправляем true
        return isPostPublishSuccess;
    }


    private void removeDuplicates(List<WebImage> imagesList) {
        Set<WebImage> uniqueList = new HashSet<>(imagesList);
        imagesList.clear();
        imagesList.addAll(uniqueList);
    }


    private boolean publishCurrentPost(String attachedPhotosId, VkApiClient vk, UserActor actor) {

        boolean isPostPublishSuccess = false;

        log.info("PUBLISHING POST...");
        //пока публикация не будет удачна повторять
        while (!isPostPublishSuccess) {
            try {
                PostResponse postResponse = vk.wall().post(actor)
                        .ownerId(GROUP_ID)
                        .attachments(attachedPhotosId)
                        .execute();
                log.info(postResponse.toPrettyString());
                isPostPublishSuccess = true;

            } catch (ApiException | ClientException e) {
                log.warning("ERROR #3: Error publishing current post on wall: " + e.getMessage());
                log.warning("Retrying publishing current post...");
            }
        }

        return isPostPublishSuccess;
    }


    private void deleteDownloadedFiles(List<File>fileList) {
        log.info("Cleaning downloaded files...");
        for (File file : fileList) {
            FileUtils.deleteQuietly(file);
        }
    }

}