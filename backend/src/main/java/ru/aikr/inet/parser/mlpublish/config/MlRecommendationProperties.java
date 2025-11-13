package ru.aikr.inet.parser.mlpublish.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ML publishing recommendation service.
 */
@ConfigurationProperties(prefix = "ml.publish")
public class MlRecommendationProperties {

    private String baseUrl;
    private String apiKey;
    private String recommendationPath = "/recommend";
    private int timeoutSeconds = 6;
    private boolean requireApiKey = false;
    private int maxBatchSize = 100;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getRecommendationPath() {
        return recommendationPath;
    }

    public void setRecommendationPath(String recommendationPath) {
        this.recommendationPath = recommendationPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isRequireApiKey() {
        return requireApiKey;
    }

    public void setRequireApiKey(boolean requireApiKey) {
        this.requireApiKey = requireApiKey;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }
}
