package ru.aikr.inet.parser.mlfeedback.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class JdbcMlFeedbackRepository implements MlFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMlFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int saveAll(List<MlFeedbackRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (MlFeedbackRecord record : records) {
            int updated = jdbcTemplate.update(
                    "INSERT INTO ml_feedback (candidate_id, url, hash, decision, score, reason, zone) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    record.getCandidateId(),
                    record.getUrl(),
                    record.getHash(),
                    record.getDecision(),
                    record.getScore(),
                    record.getReason(),
                    record.getZone()
            );
            total += updated;
        }
        return total;
    }

    @Override
    public List<MlFeedbackRecord> findFeedback(int limit, int offset, Instant since) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, candidate_id, url, hash, decision, score, reason, zone, created_at FROM ml_feedback");
        List<Object> params = new ArrayList<>();
        if (since != null) {
            sql.append(" WHERE created_at >= ?");
            params.add(Timestamp.from(since));
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    private MlFeedbackRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        MlFeedbackRecord record = new MlFeedbackRecord();
        record.setId(rs.getLong("id"));
        long candidateId = rs.getLong("candidate_id");
        if (!rs.wasNull()) {
            record.setCandidateId(candidateId);
        }
        record.setUrl(rs.getString("url"));
        record.setHash(rs.getString("hash"));
        record.setDecision(Objects.requireNonNull(rs.getString("decision")));
        double score = rs.getDouble("score");
        if (!rs.wasNull()) {
            record.setScore(score);
        }
        record.setReason(rs.getString("reason"));
        record.setZone(rs.getString("zone"));
        Timestamp created = rs.getTimestamp("created_at");
        record.setCreatedAt(created != null ? created.toInstant() : null);
        return record;
    }
}
