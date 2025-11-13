package ru.aikr.inet.parser.history.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class JdbcVkSyncCheckpointRepository implements VkSyncCheckpointRepository {

    private static final String KEY = "vk.sync.since";

    private final JdbcTemplate jdbcTemplate;

    public JdbcVkSyncCheckpointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Instant getSince() {
        return jdbcTemplate.query(
                        "SELECT value FROM kv WHERE name = ?",
                        (rs, rowNum) -> rs.getString("value"),
                        KEY
                )
                .stream()
                .findFirst()
                .map(Instant::parse)
                .orElse(null);
    }

    @Override
    public void saveSince(Instant since) {
        if (since == null) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO kv(name, value) VALUES(?, ?) " +
                        "ON CONFLICT(name) DO UPDATE SET value = excluded.value",
                KEY,
                since.toString()
        );
    }
}
