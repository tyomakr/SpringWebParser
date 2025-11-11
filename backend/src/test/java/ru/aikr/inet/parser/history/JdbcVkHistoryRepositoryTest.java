package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import ru.aikr.inet.parser.history.model.VkImageHistoryRecord;
import ru.aikr.inet.parser.history.repository.JdbcVkHistoryRepository;
import ru.aikr.inet.parser.history.repository.VkHistoryRepository;
import ru.aikr.inet.parser.logging.LogEventsPublisher;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@JdbcTest
@Import({JdbcVkHistoryRepository.class, JdbcVkHistoryRepositoryTest.TestConfig.class})
@TestPropertySource(properties = {
        // заставляем Spring прогнать schema.sql для H2
        "spring.sql.init.mode=always"
})
class JdbcVkHistoryRepositoryTest {

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
                .containsExactly("alpha", "beta");
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
                .containsExactly("second", "third");
    }

    @Test
    void shouldFilterBySinceInclusive() {
        Instant boundary = Instant.parse("2023-01-02T00:00:00Z");
        insert("before", Instant.parse("2023-01-01T00:00:00Z"), true);
        insert("boundary", boundary, true);
        insert("after", Instant.parse("2023-01-03T00:00:00Z"), true);

        List<VkImageHistoryRecord> filtered = repository.findTrainingBatch(10, 0, boundary);

        assertThat(filtered.stream().map(VkImageHistoryRecord::getHash).toList())
                .containsExactly("boundary", "after");
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
}
