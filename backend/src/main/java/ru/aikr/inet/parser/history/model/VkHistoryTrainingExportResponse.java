package ru.aikr.inet.parser.history.model;

import java.time.Instant;

public record VkHistoryTrainingExportResponse(
        Long id,
        String url,
        String hash,
        Instant createdAt,
        String mlDecision,
        Double mlScore,
        String mlReason) {

    public static VkHistoryTrainingExportResponse fromRecord(VkImageHistoryRecord record) {
        return new VkHistoryTrainingExportResponse(
                record.getId(),
                record.getUrl(),
                record.getHash(),
                record.getCreatedAt(),
                record.getMlDecision(),
                record.getMlScore(),
                record.getMlReason()
        );
    }
}
