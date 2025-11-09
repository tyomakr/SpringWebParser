package ru.aikr.inet.parser.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства доступа к VK API (пользователь/группа/токен).
 */
@ConfigurationProperties(prefix = "vk")
public class VkProperties {

    private Long appId;
    private Long userId;
    private Long groupId;
    private String accessToken;

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}