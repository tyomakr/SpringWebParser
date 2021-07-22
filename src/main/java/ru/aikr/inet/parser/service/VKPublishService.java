package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.domain.WebImage;

import java.util.List;

public interface VKPublishService {

    void postToWall(List<WebImage> imageList);
}
