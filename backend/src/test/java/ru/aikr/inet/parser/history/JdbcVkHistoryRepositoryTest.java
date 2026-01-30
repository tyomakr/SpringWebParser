package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.domain.Page;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.repository.JdbcVkHistoryRepository;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.logging.service.LogEventsPublisher;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcVkHistoryRepository.class, JdbcVkHistoryRepositoryTest.TestConfig.class})
@TestPropertySource(properties = {
        // заставляем Spring прогнать schema.sql
        "spring.sql.init.mode=always"
})
class JdbcVkHistoryRepositoryTest {

    private static final String H2_URL = "jdbc:h2:mem:vk_history_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    private static PostgreSQLContainer<?> postgres;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (isDockerAvailable()) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("swp_backend_test")
                        .withUsername("swp")
                        .withPassword("swp_pass");
                postgres.start();
            }
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        } else {
            registry.add("spring.datasource.url", () -> H2_URL);
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        LogEventsPublisher logEventsPublisher() {
            // мок, чтобы не тянуть остальную инфраструктуру логов
            return mock(LogEventsPublisher.class);
        }
    }

    @Autowired
    private VkHistoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldReturnOnlyTrainingRecordsOrderedByCreatedAtAndId() {
        insert("alpha", Instant.parse("2023-01-01T00:00:00Z"), true);
        insert("beta", Instant.parse("2023-01-02T00:00:00Z"), true);
        insert("gamma", Instant.parse("2023-01-03T00:00:00Z"), false);

        List<VkImageHistoryRecord> result = repository.findTrainingBatch(10, 0, null);

        assertThat(result).hasSize(2);
        assertThat(result.stream().allMatch(r -> Boolean.TRUE.equals(r.getUseForTraining()))).isTrue();
        assertThat(result.stream().map(VkImageHistoryRecord::getHash).toList())
                .containsExactly("beta", "alpha");
    }

    @Test
    void shouldSupportPaginationWithStableSorting() {
        Instant sameTs = Instant.parse("2023-01-01T00:00:00Z");
        insert("first", sameTs, true);
        insert("second", sameTs, true);
        insert("third", Instant.parse("2023-01-02T00:00:00Z"), true);

        List<VkImageHistoryRecord> page = repository.findTrainingBatch(2, 1, null);

        assertThat(page).hasSize(2);
        assertThat(page.stream().map(VkImageHistoryRecord::getHash).toList())
                .containsExactly("second", "first");
    }

    @Test
    void shouldFilterBySinceInclusive() {
        Instant boundary = Instant.parse("2023-01-02T00:00:00Z");
        insert("before", Instant.parse("2023-01-01T00:00:00Z"), true);
        insert("boundary", boundary, true);
        insert("after", Instant.parse("2023-01-03T00:00:00Z"), true);

        List<VkImageHistoryRecord> filtered = repository.findTrainingBatch(10, 0, boundary);

        assertThat(filtered.stream().map(VkImageHistoryRecord::getHash).toList())
                .containsExactly("after", "boundary");
    }

    private void insert(String hash, Instant createdAt, boolean useForTraining) {
        jdbcTemplate.update(
                "INSERT INTO vk_image_history (post_id, url, hash, created_at, ml_decision, ml_score, ml_reason, use_for_training) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                123L,
                "https://example.com/" + hash,
                hash,
                createdAt != null ? Timestamp.from(createdAt) : null,
                "PUBLISH",
                0.5,
                "reason",
                useForTraining
        );
    }

    @Test
    void shouldReturnPagedEntriesOrderedByCreatedAtAndId() {
        insert("alpha", Instant.parse("2023-01-01T00:00:00Z"), true);
        insert("beta", Instant.parse("2023-01-02T00:00:00Z"), false);
        insert("gamma", Instant.parse("2023-01-03T00:00:00Z"), true);

        Page<VkImageHistoryRecord> page = repository.findPage(2, 0, null, null);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().stream().map(VkImageHistoryRecord::getHash).toList())
                .containsExactly("gamma", "beta");
    }

    @Test
    void countAppliesUseForTrainingAndSinceFilters() {
        insert("zero", Instant.parse("2023-01-01T00:00:00Z"), true);
        insert("one", Instant.parse("2023-01-02T00:00:00Z"), true);
        insert("two", Instant.parse("2023-01-03T00:00:00Z"), false);

        long totalTraining = repository.count(true, Instant.parse("2023-01-02T00:00:00Z"));

        assertThat(totalTraining).isEqualTo(1);
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception ex) {
            return false;
        }
    }
}
