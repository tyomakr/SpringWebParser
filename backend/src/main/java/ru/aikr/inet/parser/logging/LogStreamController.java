package ru.aikr.inet.parser.logging;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE‑контроллер на WebFlux.
 * — сначала выдаёт буфер последних 200 строк (publisher.getLatestLogs()),
 * — затем «живые» события из publisher.getFlux().
 */
@RestController
@CrossOrigin(origins = "http://localhost:3333")
public class LogStreamController {

    private final LogEventsPublisher publisher;

    public LogStreamController(LogEventsPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * @param skipCache если true — выдаём только "живые" события, без буфера.
     */
    @GetMapping(path = "/api/v1/logs/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs(
            @RequestParam(name = "skipCache", defaultValue = "false") boolean skipCache) {

        // Flux живых событий
        Flux<ServerSentEvent<String>> live = publisher.getFlux()
                .map(line -> ServerSentEvent.<String>builder()
                        .event("log")
                        .data(line)
                        .build());

        if (skipCache) {
            return live;
        }

        // Flux из буфера + живые события
        Flux<ServerSentEvent<String>> cached = Flux
                .fromIterable(publisher.getLatestLogs())
                .map(line -> ServerSentEvent.<String>builder()
                        .event("log")
                        .data(line)
                        .build());

        return Flux.concat(cached, live);
    }
}