package ru.aikr.inet.parser.history;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

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

    @GetMapping("/entries")
    public Mono<List<VkHistoryEntryResponse>> entries() {
        return Mono.fromCallable(() ->
                historyService.getHistoryEntries().stream()
                        .map(VkHistoryEntryResponse::fromRecord)
                        .toList()
        );
    }

    @GetMapping("/training")
    public Mono<List<VkHistoryEntryResponse>> training() {
        return Mono.fromCallable(() ->
                historyService.getTrainingEntries().stream()
                        .map(VkHistoryEntryResponse::fromRecord)
                        .toList()
        );
    }

    @PatchMapping("/entries/{id}/training")
    public Mono<Void> updateTrainingFlag(@PathVariable long id,
                                         @RequestBody VkHistoryTrainingToggleRequest request) {
        return Mono.fromRunnable(() ->
                historyService.updateUseForTraining(id, Boolean.TRUE.equals(request.getUseForTraining()))
        );
    }
}
