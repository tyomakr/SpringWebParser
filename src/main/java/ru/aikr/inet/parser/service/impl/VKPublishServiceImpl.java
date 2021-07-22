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
import java.util.List;

@Service
@RequiredArgsConstructor
public class VKPublishServiceImpl implements VKPublishService {

    private static final TransportClient transportClient = HttpTransportClient.getInstance();
    private static final VkApiClient vk = new VkApiClient(transportClient);

    @Value("${vk.user-id}")
    private Integer USER_ID;
    @Value("${vk.group-id}")
    private Integer GROUP_ID;
    @Value("${vk.access-token}")
    private String ACCESS_TOKEN;


    @Override
    public void postToWall(List<WebImage> fullImagesList) {
        //get UserActor
        UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

        //преобразуем полный список WebImage в список File, попутно выкачивая файлы
        List<File> fileList = convertWebImageListToFileList(fullImagesList);

        //делим количественно по 10 шт. (на каждый пост)
        List<List<File>> chunkedLists = chunkify(fileList, 10);

        //createPost в цикле
        for (List<File> chunkedList : chunkedLists) {
            createPost(userActor, chunkedList);
        }

        //удаляем скачанные файлы
        deleteDownloadedFiles(fileList);
        System.out.println("DEBUG: COMPLETE");
    }


    private static List<File> convertWebImageListToFileList(List<WebImage> webImageList) {
        List<File> fileList = new ArrayList<>();

        try {
            for (WebImage webImage : webImageList) {
                URL currentURL = new URL(webImage.getDirectLink());
                BufferedImage img = ImageIO.read(currentURL);
                File file = new File("file" + FilenameUtils.getName(currentURL.getPath()));
                ImageIO.write(img, "jpg", file);
                fileList.add(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            Thread.sleep(1000);
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

