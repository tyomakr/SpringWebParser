package ru.aikr.inet.parser.history.controller;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.aikr.inet.parser.history.model.VkHistoryEntryResponse;
import ru.aikr.inet.parser.history.model.VkHistoryStats;
import ru.aikr.inet.parser.history.model.VkHistoryTrainingExportResponse;
import ru.aikr.inet.parser.history.model.VkHistoryTrainingToggleRequest;
import ru.aikr.inet.parser.history.model.VkWallSyncReport;
import ru.aikr.inet.parser.history.service.VkHistoryService;
import ru.aikr.inet.parser.history.model.VkWallSyncStatus;
import ru.aikr.inet.parser.history.service.VkWallSyncScheduler;
import ru.aikr.inet.parser.history.service.VkWallSyncService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/vk-history")
public class VkHistoryController {

    private final VkHistoryService historyService;
    private final VkWallSyncService wallSyncService;
    private final VkWallSyncScheduler syncScheduler;
    private final Environment environment;

    public VkHistoryController(VkHistoryService historyService,
                               VkWallSyncService wallSyncService,
                               VkWallSyncScheduler syncScheduler,
                               Environment environment) {
        this.historyService = historyService;
        this.wallSyncService = wallSyncService;
        this.syncScheduler = syncScheduler;
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

    @PostMapping("/sync-wall")
    public Mono<VkWallSyncReport> syncWall(@RequestParam(required = false) String since,
                                           @RequestParam(defaultValue = "3") int pages) {
        Instant sinceInstant = parseSince(since);
        int safePages = Math.max(pages, 1);
        return Mono.fromCallable(() -> wallSyncService.syncWall(sinceInstant, safePages))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sync-wall/status")
    public Mono<VkWallSyncStatus> syncStatus() {
        return Mono.just(syncScheduler.getStatus());
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
