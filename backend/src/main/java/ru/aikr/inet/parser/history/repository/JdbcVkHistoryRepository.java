package ru.aikr.inet.parser.history.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcVkHistoryRepository implements VkHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVkHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(VkImageHistoryRecord record) {
        Instant createdAtInstant = record.getCreatedAt();
        Timestamp createdAt = createdAtInstant != null ? Timestamp.from(createdAtInstant) : null;

        Boolean useForTraining = record.getUseForTraining();
        if (useForTraining == null) {
            useForTraining = Boolean.TRUE;
        }

        int updated = jdbcTemplate.update(
                "MERGE INTO vk_image_history (" +
                        "hash, post_id, url, created_at, synced_at, ml_decision, ml_score, ml_reason, use_for_training" +
                        ") KEY (hash) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)",
                record.getHash(),
                record.getPostId(),
                record.getUrl(),
                createdAt,
                record.getMlDecision(),
                record.getMlScore(),
                record.getMlReason(),
                useForTraining);
        return updated > 0;
    }

    @Override
    public long count() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vk_image_history",
                Long.class);
        return value != null ? value : 0L;
    }

    @Override
    public Instant lastSynced() {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT MAX(synced_at) FROM vk_image_history",
                Timestamp.class);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    @Override
    public List<VkImageHistoryRecord> findAll() {
        return jdbcTemplate.query("SELECT * FROM vk_image_history ORDER BY created_at DESC",
                this::mapRow);
    }

    @Override
    public List<VkImageHistoryRecord> findTrainingBatch(int limit, int offset, Instant since) {
        String baseSelect = "SELECT id, post_id, url, hash, created_at, synced_at, " +
                "ml_decision, ml_score, ml_reason, use_for_training FROM vk_image_history " +
                "WHERE use_for_training = TRUE";

        if (since != null) {
            return jdbcTemplate.query(
                    baseSelect + " AND created_at >= ? ORDER BY created_at, id LIMIT ? OFFSET ?",
                    this::mapRow,
                    Timestamp.from(since),
                    limit,
                    offset
            );
        }

        return jdbcTemplate.query(
                baseSelect + " ORDER BY created_at, id LIMIT ? OFFSET ?",
                this::mapRow,
                limit,
                offset
        );
    }

    private VkImageHistoryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        VkImageHistoryRecord record = new VkImageHistoryRecord();
        record.setId(rs.getLong("id"));
        record.setPostId(rs.getLong("post_id"));
        record.setUrl(rs.getString("url"));
        record.setHash(rs.getString("hash"));
        Timestamp created = rs.getTimestamp("created_at");
        record.setCreatedAt(created != null ? created.toInstant() : null);
        Timestamp synced = rs.getTimestamp("synced_at");
        record.setSyncedAt(synced != null ? synced.toInstant() : null);
        record.setMlDecision(rs.getString("ml_decision"));
        double score = rs.getDouble("ml_score");
        if (!rs.wasNull()) {
            record.setMlScore(score);
        }
        record.setMlReason(rs.getString("ml_reason"));
        record.setUseForTraining(rs.getBoolean("use_for_training"));
        return record;
    }

    @Override
    public boolean updateUseForTraining(long id, boolean useForTraining) {
        int updated = jdbcTemplate.update(
                "UPDATE vk_image_history SET use_for_training = ? WHERE id = ?",
                useForTraining,
                id);
        return updated > 0;
    }
}
