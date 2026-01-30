package ru.aikr.inet.parser.history.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcVkHistoryRepository implements VkHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcVkHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(VkImageHistoryRecord record) {
        Instant createdAtInstant = record.getCreatedAt();
        Timestamp createdAt = toTimestamp(createdAtInstant);

        Boolean useForTraining = record.getUseForTraining();
        if (useForTraining == null) {
            useForTraining = Boolean.TRUE;
        }

        int updated = jdbcTemplate.update(
                "INSERT INTO vk_image_history (" +
                        "hash, post_id, url, created_at, synced_at, ml_decision, ml_score, ml_reason, use_for_training" +
                        ") VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?) " +
                        "ON CONFLICT (hash) DO UPDATE SET " +
                        "post_id = EXCLUDED.post_id, " +
                        "url = EXCLUDED.url, " +
                        "created_at = EXCLUDED.created_at, " +
                        "synced_at = CURRENT_TIMESTAMP, " +
                        "ml_decision = EXCLUDED.ml_decision, " +
                        "ml_score = EXCLUDED.ml_score, " +
                        "ml_reason = EXCLUDED.ml_reason, " +
                        "use_for_training = EXCLUDED.use_for_training",
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
    public boolean saveIfAbsent(VkImageHistoryRecord record) {
        Objects.requireNonNull(record.getHash(), "hash must be provided");
        if (existsByHash(record.getHash())) {
            return false;
        }
        int inserted = insertRecord(record);
        return inserted > 0;
    }

    @Override
    public boolean existsByHash(String hash) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vk_image_history WHERE hash = ?",
                Integer.class,
                hash);
        return count != null && count > 0;
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
        Page<VkImageHistoryRecord> page = findPage(limit, offset, Boolean.TRUE, since);
        return page.getContent();
    }

    @Override
    public Page<VkImageHistoryRecord> findPage(int limit, int offset, Boolean useForTraining, Instant since) {
        int safeLimit = Math.max(limit, 1);
        int safeOffset = Math.max(offset, 0);
        StringBuilder query = new StringBuilder(
                "SELECT id, post_id, url, hash, created_at, synced_at, " +
                        "ml_decision, ml_score, ml_reason, use_for_training FROM vk_image_history"
        );
        List<Object> params = applyFilters(query, useForTraining, since);
        query.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(safeLimit);
        params.add(safeOffset);

        String sql = Objects.requireNonNull(query.toString());
        Object[] queryArgs = params.toArray();
        List<VkImageHistoryRecord> items = jdbcTemplate.query(
                sql,
                this::mapRow,
                queryArgs
        );
        long total = count(useForTraining, since);
        int pageNumber = safeOffset / safeLimit;
        PageRequest pageRequest = PageRequest.of(pageNumber, safeLimit,
                Sort.by(Sort.Order.desc("created_at"), Sort.Order.desc("id")));
        return new PageImpl<>(items, pageRequest, total);
    }

    @Override
    public long count(Boolean useForTraining, Instant since) {
        StringBuilder query = new StringBuilder("SELECT COUNT(*) FROM vk_image_history");
        List<Object> params = applyFilters(query, useForTraining, since);
        String sql = Objects.requireNonNull(query.toString());
        Object[] queryArgs = params.toArray();
        Long value = jdbcTemplate.queryForObject(sql, Long.class, queryArgs);
        return value != null ? value : 0L;
    }

    private List<Object> applyFilters(StringBuilder query, Boolean useForTraining, Instant since) {
        List<Object> params = new ArrayList<>();
        boolean whereAdded = false;
        if (useForTraining != null) {
            query.append(" WHERE use_for_training = ?");
            params.add(useForTraining);
            whereAdded = true;
        }
        if (since != null) {
            query.append(whereAdded ? " AND " : " WHERE ").append("created_at >= ?");
            params.add(Timestamp.from(since));
        }
        return params;
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

    private int insertRecord(VkImageHistoryRecord record) {
        Instant createdAtInstant = record.getCreatedAt();
        Timestamp createdAt = toTimestamp(createdAtInstant);
        Boolean useForTraining = record.getUseForTraining();
        if (useForTraining == null) {
            useForTraining = Boolean.TRUE;
        }
        return jdbcTemplate.update(
                "INSERT INTO vk_image_history (" +
                        "hash, post_id, url, created_at, ml_decision, ml_score, ml_reason, use_for_training" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                record.getHash(),
                record.getPostId(),
                record.getUrl(),
                createdAt,
                record.getMlDecision(),
                record.getMlScore(),
                record.getMlReason(),
                useForTraining);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
