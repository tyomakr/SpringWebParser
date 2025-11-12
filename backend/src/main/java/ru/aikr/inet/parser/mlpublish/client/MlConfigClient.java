package ru.aikr.inet.parser.mlpublish.client;

import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;

import java.util.Collections;
import java.util.List;

public interface MlConfigClient {

    Mono<MlConfigResponse> config();
}
