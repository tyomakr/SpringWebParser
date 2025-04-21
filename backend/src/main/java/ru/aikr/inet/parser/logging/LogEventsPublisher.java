package ru.aikr.inet.parser.logging;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Буфер последних 200 строк лога и реактивный стрим для SSE.
 */
@Component
public class LogEventsPublisher {

    /** Синк для «живых» событий */
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    /** Буфер для REST‑запроса последних 200 строк */
    private final Deque<String> buffer = new ArrayDeque<>(200);

    /** Публикует строку одновременно в буфер и в синк */
    public void publish(String msg) {
        sink.tryEmitNext(msg);
        synchronized (buffer) {
            if (buffer.size() >= 200) buffer.removeFirst();
            buffer.addLast(msg);
        }
    }

    /** REST‑эндпоинт для последних 200 строк */
    public List<String> getLatestLogs() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    /** Flux для SSE‑стрима новых записей */
    public Flux<String> getFlux() {
        return sink.asFlux();
    }
}
