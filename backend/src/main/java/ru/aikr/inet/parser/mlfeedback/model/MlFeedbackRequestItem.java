package ru.aikr.inet.parser.mlfeedback.model;

import jakarta.validation.constraints.NotBlank;

public record MlFeedbackRequestItem(
        Long candidateId,
        @NotBlank String url,
        @NotBlank String hash,
        @NotBlank String decision,
        Double score,
        String reason,
        String zone
) {
}
