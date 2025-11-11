package ru.aikr.inet.parser.logging.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
public class LogStreamController {

    private final LogEventsPublisher publisher;

    public LogStreamController(LogEventsPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs(
            @RequestParam(name = "skipCache", defaultValue = "false") boolean skipCache) {
        log.info("[SSE] new connection, skipCache={}", skipCache);

        // сразу пингуем, чтобы браузер не считал соединение пустым
        Flux<ServerSentEvent<String>> ping = Flux.just(
                ServerSentEvent.<String>builder().comment("connected").build()
        );

        // по необходимости отдаем буфер последних 200
        Flux<ServerSentEvent<String>> cached = skipCache
                ? Flux.empty()
                : Flux.fromIterable(publisher.getLatestLogs())
                .map(line -> ServerSentEvent.<String>builder().event("log").data(line).build());

        // живые логи от ReactiveAppender
        Flux<ServerSentEvent<String>> live = publisher.getFlux()
                .map(line -> ServerSentEvent.<String>builder().event("log").data(line).build());

        // heartbeat, чтобы держать канал открытым
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<String>builder().comment("heartbeat").build());

        // раньше у тебя было Flux.merge(ping, cached, live, heartbeat) — меняем на:
        Flux<ServerSentEvent<String>> main = Flux.concat(ping, cached, live);
        return main
                .mergeWith(heartbeat)
                .doOnSubscribe(s -> log.info("[SSE] subscribed"))
                .doOnCancel(()  -> log.info("[SSE] canceled"));
    }

}
