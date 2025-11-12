package ru.aikr.inet.parser.mlpublish.client;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;

public class HttpMlConfigClient implements MlConfigClient {

    private final WebClient webClient;

    public HttpMlConfigClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<MlConfigResponse> config() {
        return webClient.get()
                .uri("/config")
                .retrieve()
                .bodyToMono(MlConfigResponse.class);
    }
}
