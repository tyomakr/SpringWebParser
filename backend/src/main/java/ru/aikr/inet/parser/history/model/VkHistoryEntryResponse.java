package ru.aikr.inet.parser.history.model;

public record VkHistoryEntryResponse(
        long id,
        String url,
        String hash,
        String mlDecision,
        Double mlScore,
        String mlReason,
        Boolean useForTraining
) {

    public static VkHistoryEntryResponse fromRecord(VkImageHistoryRecord record) {
        return new VkHistoryEntryResponse(
                record.getId(),
                record.getUrl(),
                record.getHash(),
                record.getMlDecision(),
                record.getMlScore(),
                record.getMlReason(),
                record.getUseForTraining()
        );
    }
}
