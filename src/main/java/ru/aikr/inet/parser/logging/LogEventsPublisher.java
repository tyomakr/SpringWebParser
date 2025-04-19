package ru.aikr.inet.parser.logging;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Хранит буфер последних 200 строк лога и при этом выпускает Flux
 */
@Component
public class LogEventsPublisher {
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final Deque<String> buffer = new ArrayDeque<>(200);

    public void publish(String msg) {
        sink.tryEmitNext(msg);
        synchronized (buffer) {
            if (buffer.size() >= 200) buffer.removeFirst();
            buffer.addLast(msg);
        }
    }

    public List<String> getLatestLogs() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    public reactor.core.publisher.Flux<String> getFlux() {
        return sink.asFlux();
    }
}
