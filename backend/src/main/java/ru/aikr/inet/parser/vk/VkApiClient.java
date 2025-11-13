package ru.aikr.inet.parser.vk;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.aikr.inet.parser.vk.dto.WallGetResponse;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class VkApiClient {

    private static final Duration API_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final VkApiProperties properties;

    public VkApiClient(WebClient.Builder builder, VkApiProperties properties) {
        this.properties = properties;
        String baseUrl = Objects.requireNonNull(properties.getBaseUrl(), "vk.api.base-url must not be null");
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) API_TIMEOUT.toMillis())
                .responseTimeout(API_TIMEOUT)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(API_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)));
        HttpClient safeClient = Objects.requireNonNull(httpClient, "HttpClient must not be null");
        this.webClient = builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(safeClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build())
                .build();
    }

    public Mono<WallGetResponse> wallGet(long ownerId, int count, int offset) {
        String token = properties.getToken();
        if (!StringUtils.hasText(token)) {
            return Mono.error(new IllegalStateException("vk.api.token must be configured"));
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/method/wall.get")
                        .queryParam("owner_id", ownerId)
                        .queryParam("count", count)
                        .queryParam("offset", offset)
                        .queryParam("v", properties.getVersion())
                        .queryParam("access_token", token)
                        .build())
                .retrieve()
                .bodyToMono(WallGetResponse.class);
    }
}
