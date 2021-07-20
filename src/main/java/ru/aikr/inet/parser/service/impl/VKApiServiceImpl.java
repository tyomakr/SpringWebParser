package ru.aikr.inet.parser.service.impl;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.AuthResponse;
import com.vk.api.sdk.objects.photos.Photo;
import com.vk.api.sdk.objects.photos.PhotoUpload;
import com.vk.api.sdk.objects.photos.responses.GetResponse;
import com.vk.api.sdk.objects.photos.responses.GetWallUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.SaveWallPhotoResponse;
import com.vk.api.sdk.objects.photos.responses.WallUploadResponse;
import com.vk.api.sdk.objects.wall.responses.PostResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.service.VKApiService;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VKApiServiceImpl implements VKApiService {

    private static final TransportClient transportClient = HttpTransportClient.getInstance();
    private static final VkApiClient vk = new VkApiClient(transportClient);

    @Value("${vk.user-id}")
    private final Integer USER_ID;
    @Value("${vk.group-id}")
    private final Integer GROUP_ID;
    @Value("${vk.access-token}")
    private final String ACCESS_TOKEN;


    public VkApiClient getVkApiClient() {
        return new VkApiClient(transportClient);
    }


    @Override
    public void postToWall() {
        //get UserActor
        UserActor userActor = new UserActor(USER_ID, ACCESS_TOKEN);

        //получаем полный список картинок
        //делим количественно по 10 шт. (с округлением в большую сторону - Math.ceil(num))
        //createPost в цикле

        //после чистим локальное хранилище от скачанных картинок







    }

    @SneakyThrows
    private void createPost(UserActor actor, List<File> fileList) {

        StringBuilder attachIds = new StringBuilder();

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
        }

        //del last comma
        attachIds.delete(attachIds.length() - 1, attachIds.length());

        //post to group wall
        vk.wall().post(actor)
                .ownerId(GROUP_ID)
                .attachments(attachIds.toString())
                .execute();
    }


}

