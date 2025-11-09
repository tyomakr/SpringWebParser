package ru.aikr.inet.parser.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Свойства для подключения к ML Recommendation API.
 */
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationProperties {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}