package ru.aikr.inet.parser.history.model;

public record VkWallSyncReport(
        int postsFetched,
        int photosFound,
        int inserted,
        int skipped
) {
}
