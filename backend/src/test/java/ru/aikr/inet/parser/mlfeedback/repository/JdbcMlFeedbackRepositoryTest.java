package ru.aikr.inet.parser.mlfeedback.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class JdbcMlFeedbackRepositoryTest {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private MlFeedbackRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:h2:mem:mlfeedback" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        dataSource = new SingleConnectionDataSource(url, true);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("schema.sql"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcMlFeedbackRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        ((SingleConnectionDataSource) dataSource).destroy();
    }

    @Test
    void saveAllInsertsRecords() {
        List<MlFeedbackRecord> records = List.of(
                new MlFeedbackRecord(1L, "https://example.com/1", "hash-1", "PUBLISH", 0.5, "reason a", "hit"),
                new MlFeedbackRecord(2L, "https://example.com/2", "hash-2", "SKIP", null, "reason b", "miss")
        );

        int saved = repository.saveAll(records);
        assertThat(saved).isEqualTo(2);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ml_feedback", Integer.class)).isEqualTo(2);
    }

    @Test
    void findFeedbackRespectsSinceAndPagination() {
        List<MlFeedbackRecord> records = List.of(
                new MlFeedbackRecord(1L, "https://example.com/1", "hash-1", "PUBLISH", 0.5, "reason a", "hit"),
                new MlFeedbackRecord(2L, "https://example.com/2", "hash-2", "SKIP", null, "reason b", "miss")
        );
        repository.saveAll(records);

        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE ml_feedback SET created_at = ? WHERE id = ?", Timestamp.from(now.minusSeconds(60)), 1);

        List<MlFeedbackRecord> filtered = repository.findFeedback(10, 0, now.minusSeconds(30));
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getCandidateId()).isEqualTo(2L);
    }
}
