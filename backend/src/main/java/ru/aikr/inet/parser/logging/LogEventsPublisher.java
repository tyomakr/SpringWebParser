package ru.aikr.inet.parser.logging;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Буфер последних 200 строк лога + реактивный Sinks для SSE.
 */
@Slf4j
@Component
public class LogEventsPublisher {

    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);
    private final Deque<String> buffer = new ArrayDeque<>(200);

    /**
     * Регистрируем себя в Log4j2ReactiveAppender сразу после
     * старта Spring-контекста.
     */
    @PostConstruct
    public void init() {
        log.info("[SSE publisher] registering ReactiveAppender");
        Log4j2ReactiveAppender.setPublisher(this);
    }

    /**
     * Вызывается из аппендера — сразу шлёт всем подписчикам
     * и кладёт в FIFO-буфер (до 200 строк).
     */
    public void publish(String msg) {
        sink.tryEmitNext(msg);
        synchronized (buffer) {
            if (buffer.size() >= 200) buffer.removeFirst();
            buffer.addLast(msg);
        }
    }

    /** REST-запрос «последних 200 строк» */
    public List<String> getLatestLogs() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /** Flux живых событий (SSE). */
    public Flux<String> getFlux() {
        return sink.asFlux();
    }
}