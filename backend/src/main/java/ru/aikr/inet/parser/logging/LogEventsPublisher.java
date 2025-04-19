package ru.aikr.inet.parser.logging;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Буфер последних 200 строк лога.
 * Используется REST‑контроллером /api/v1/logs/latest.
 */
@Component
public class LogEventsPublisher {

    /** Кольцевой буфер 200 строк. */
    private final Deque<String> buffer = new ArrayDeque<>(200);

    public void publish(String msg) {
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
}

