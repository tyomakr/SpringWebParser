package ru.aikr.inet.parser.mlfeedback.model;

import java.time.Instant;

public record MlFeedbackResponseItem(
        Long id,
        Long candidateId,
        String url,
        String hash,
        String decision,
        Double score,
        String reason,
        String zone,
        Instant createdAt
) {}
