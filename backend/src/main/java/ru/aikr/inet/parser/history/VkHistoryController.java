package ru.aikr.inet.parser.history;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/vk-history")
@RequiredArgsConstructor
public class VkHistoryController {

    private final VkHistoryService historyService;

    @PostMapping("/refresh")
    public Mono<VkHistoryStats> refresh() {
        return Mono.fromCallable(historyService::refreshFromVk);
    }

    @GetMapping("/stats")
    public Mono<VkHistoryStats> stats() {
        return Mono.fromCallable(historyService::currentStats);
    }
}