package ru.aikr.inet.parser.mlpublish;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Configuration
@EnableConfigurationProperties(MlRecommendationProperties.class)
public class MlRecommendationClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public WebClient mlRecommendationWebClient(WebClient.Builder builder,
                                               MlRecommendationProperties properties) {
        WebClient.Builder clientBuilder = builder.baseUrl(properties.getBaseUrl());
        if (StringUtils.hasText(properties.getApiKey())) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        return clientBuilder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ml.publish", name = "base-url")
    public MlRecommendationClient mlRecommendationClient(WebClient mlRecommendationWebClient,
                                                         MlRecommendationProperties properties) {
        return new HttpMlRecommendationClient(mlRecommendationWebClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MlRecommendationClient.class)
    public MlRecommendationClient noopMlRecommendationClient() {
        return candidates -> Mono.just(Collections.emptyList());
    }
}
