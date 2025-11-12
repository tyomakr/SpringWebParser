package ru.aikr.inet.parser.mlpublish.client;

import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;

public interface MlConfigClient {

    Mono<MlConfigResponse> config();
}
