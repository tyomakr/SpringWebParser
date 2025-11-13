package ru.aikr.inet.parser.history.repository;

import java.time.Instant;

public interface VkSyncCheckpointRepository {

    Instant getSince();

    void saveSince(Instant since);
}
