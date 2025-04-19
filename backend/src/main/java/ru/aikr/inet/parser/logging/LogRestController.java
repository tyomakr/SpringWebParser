package ru.aikr.inet.parser.logging;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LogRestController {
    private final LogEventsPublisher publisher;

    public LogRestController(LogEventsPublisher publisher) {
        this.publisher = publisher;
    }

    /** Возвращает JSON‑массив последних 200 строк лога */
    @GetMapping("/api/v1/logs/latest")
    public List<String> latest() {
        return publisher.getLatestLogs();
    }
}