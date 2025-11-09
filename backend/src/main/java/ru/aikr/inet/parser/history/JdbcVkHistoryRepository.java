package ru.aikr.inet.parser.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class JdbcVkHistoryRepository implements VkHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVkHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(VkImageHistoryRecord record) {
        Timestamp createdAt = record.getCreatedAt() != null
                ? Timestamp.from(record.getCreatedAt())
                : null;

        int updated = jdbcTemplate.update(
                "MERGE INTO vk_image_history (hash, post_id, url, created_at, synced_at) " +
                        "KEY (hash) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                record.getHash(),
                record.getPostId(),
                record.getUrl(),
                createdAt
        );

        return updated > 0;
    }

    @Override
    public long count() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vk_image_history",
                Long.class
        );
        return value != null ? value : 0L;
    }

    @Override
    public Instant lastSynced() {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT MAX(synced_at) FROM vk_image_history",
                Timestamp.class
        );
        return timestamp != null ? timestamp.toInstant() : null;
    }
}