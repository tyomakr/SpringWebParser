package ru.aikr.inet.parser.mlpublish.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlpublish.client.MlConfigClient;
import ru.aikr.inet.parser.mlpublish.model.MlConfigResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ml")
public class MlConfigController {

    private final MlConfigClient configClient;

    @GetMapping("/config")
    public Mono<MlConfigResponse> config() {
        return configClient.config();
    }
}
