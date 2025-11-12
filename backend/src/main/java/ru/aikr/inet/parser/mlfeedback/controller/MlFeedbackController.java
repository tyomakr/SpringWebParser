package ru.aikr.inet.parser.mlfeedback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRequestItem;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackResponseItem;
import ru.aikr.inet.parser.mlfeedback.service.MlFeedbackService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ml/feedback")
@Validated
@RequiredArgsConstructor
public class MlFeedbackController {

    private static final Set<String> ALLOWED_DECISIONS = Set.of("PUBLISH", "SKIP", "EXCLUDE");

    private final MlFeedbackService feedbackService;

    @PostMapping
    public Mono<MlFeedbackResponse> submitFeedback(@Valid @RequestBody List<MlFeedbackRequestItem> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must contain at least one feedback entry");
        }
        for (MlFeedbackRequestItem item : payload) {
            if (!isValidDecision(item.decision())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid decision: " + item.decision());
            }
        }
        int saved = feedbackService.saveFeedback(payload);
        return Mono.just(new MlFeedbackResponse(saved));
    }

    @GetMapping
    public Mono<List<MlFeedbackResponseItem>> listFeedback(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String since
    ) {
        Instant sinceInstant = parseSince(since);
        return Mono.fromCallable(() -> feedbackService.fetchFeedback(limit, offset, sinceInstant).stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    private Instant parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(since);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid since parameter", ex);
        }
    }

    private boolean isValidDecision(String decision) {
        return decision != null && ALLOWED_DECISIONS.contains(decision.toUpperCase());
    }

    private MlFeedbackResponseItem toResponse(MlFeedbackRecord record) {
        return new MlFeedbackResponseItem(
                record.getId(),
                record.getCandidateId(),
                record.getUrl(),
                record.getHash(),
                record.getDecision(),
                record.getScore(),
                record.getReason(),
                record.getZone(),
                record.getCreatedAt()
        );
    }

    public record MlFeedbackResponse(int saved) {}
}
