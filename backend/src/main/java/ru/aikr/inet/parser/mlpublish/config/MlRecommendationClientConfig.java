package ru.aikr.inet.parser.mlpublish.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.client.HttpMlConfigClient;
import ru.aikr.inet.parser.mlpublish.client.HttpMlRecommendationClient;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.client.MlRecommendationClient;

import java.util.Collections;
import java.util.Objects;

@Configuration
@EnableConfigurationProperties(MlRecommendationProperties.class)
public class MlRecommendationClientConfig {

    @Bean
    @Qualifier("mlRecommendationWebClient")
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public WebClient mlRecommendationWebClient(WebClient.Builder builder,
            MlRecommendationProperties properties) {
        String baseUrl = Objects.requireNonNull(
                properties.getBaseUrl(),
                "ml.publish.base-url must not be null when ml.publish.base-url is set");

        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    @Qualifier("mlStatusWebClient")
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public WebClient mlStatusWebClient(WebClient.Builder builder,
            MlRecommendationProperties properties) {
        String baseUrl = Objects.requireNonNull(
                properties.getBaseUrl(),
                "ml.publish.base-url must not be null when ml.publish.base-url is set");

        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public MlRecommendationClient mlRecommendationClient(@Qualifier("mlRecommendationWebClient") WebClient mlRecommendationWebClient,
            MlRecommendationProperties properties) {
        return new HttpMlRecommendationClient(mlRecommendationWebClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public MlConfigClient mlConfigClient(@Qualifier("mlRecommendationWebClient") WebClient mlRecommendationWebClient) {
        return new HttpMlConfigClient(mlRecommendationWebClient);
    }

    @Bean
    @ConditionalOnMissingBean(MlRecommendationClient.class)
    public MlRecommendationClient noopMlRecommendationClient() {
        return candidates -> Mono.just(Collections.emptyList());
    }
}
