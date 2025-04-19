package ru.aikr.inet.parser.service;

import ru.aikr.inet.parser.model.WebImage;

import java.util.List;

public interface VKPublishService {

    boolean generatePostsAndPublishToCommunityWall(List<WebImage> imageList);
}
