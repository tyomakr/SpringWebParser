package ru.aikr.inet.parser.recommendation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

/**
 * Конфиг для настройки WebClient и подключения RecommendationClient.
 */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "recommendation", name = "base-url")
    public WebClient recommendationWebClient(WebClient.Builder builder,
            RecommendationProperties properties) {

        String baseUrl = Objects.requireNonNull(
                properties.getBaseUrl(),
                "recommendation.base-url must not be null when recommendation.base-url is set");

        return builder
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "recommendation", name = "base-url")
    public RecommendationClient recommendationClient(WebClient recommendationWebClient) {
        return new HttpRecommendationClient(recommendationWebClient);
    }
}
