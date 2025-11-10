package ru.aikr.inet.parser.history;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class VkHistoryEntryResponse {

    private final Long id;
    private final Long postId;
    private final String url;
    private final String hash;
    private final Instant createdAt;
    private final Instant syncedAt;
    private final String mlDecision;
    private final Double mlScore;
    private final String mlReason;

    public static VkHistoryEntryResponse fromRecord(VkImageHistoryRecord record) {
        return VkHistoryEntryResponse.builder()
                .id(record.getId())
                .postId(record.getPostId())
                .url(record.getUrl())
                .hash(record.getHash())
                .createdAt(record.getCreatedAt())
                .syncedAt(record.getSyncedAt())
                .mlDecision(record.getMlDecision())
                .mlScore(record.getMlScore())
                .mlReason(record.getMlReason())
                .build();
    }
}
