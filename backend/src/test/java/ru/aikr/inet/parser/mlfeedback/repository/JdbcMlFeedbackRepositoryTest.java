package ru.aikr.inet.parser.mlfeedback.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class JdbcMlFeedbackRepositoryTest {

    private static final String H2_URL = "jdbc:h2:mem:ml_feedback_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static PostgreSQLContainer<?> postgres;

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private MlFeedbackRepository repository;

    @BeforeAll
    static void startContainerIfAvailable() {
        if (isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("swp_feedback_test")
                    .withUsername("swp")
                    .withPassword("swp_pass");
            postgres.start();
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        if (postgres != null) {
            dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword()
            );
        } else {
            dataSource = new DriverManagerDataSource(H2_URL, "sa", "");
        }
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("schema.sql"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("TRUNCATE TABLE ml_feedback");
        repository = new JdbcMlFeedbackRepository(jdbcTemplate);
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
        jdbcTemplate.update(
                "UPDATE ml_feedback SET created_at = ? WHERE hash = ?",
                Timestamp.from(now.minusSeconds(60)),
                "hash-1"
        );

        List<MlFeedbackRecord> filtered = repository.findFeedback(10, 0, now.minusSeconds(30));
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getCandidateId()).isEqualTo(2L);
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
