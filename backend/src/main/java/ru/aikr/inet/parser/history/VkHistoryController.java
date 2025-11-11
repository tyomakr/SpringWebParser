package ru.aikr.inet.parser.history;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/vk-history")
public class VkHistoryController {

    private final VkHistoryService historyService;
    private final Environment environment;

    public VkHistoryController(VkHistoryService historyService, Environment environment) {
        this.historyService = historyService;
        this.environment = environment;
    }

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

    @GetMapping("/training/export")
    public Mono<List<VkHistoryTrainingExportResponse>> trainingExport(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String since,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        requireTrainingExportAuthorization(authorizationHeader);
        Instant sinceInstant = parseSince(since);

        return Mono.fromCallable(() -> historyService.exportTraining(limit, offset, sinceInstant));
    }

    private void requireTrainingExportAuthorization(String authorizationHeader) {
        String apiKey = environment.getProperty("ml.publish.api-key");
        if (!StringUtils.hasText(apiKey)) {
            return;
        }

        String expected = "Bearer " + apiKey;
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.equals(expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }
    }

    private Instant parseSince(String since) {
        if (!StringUtils.hasText(since)) {
            return null;
        }
        try {
            return Instant.parse(since);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid since parameter", ex);
        }
    }
}
