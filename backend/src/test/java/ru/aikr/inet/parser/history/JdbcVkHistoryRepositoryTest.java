package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;

import ru.aikr.inet.parser.logging.LogEventsPublisher;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ JdbcVkHistoryRepository.class, JdbcVkHistoryRepositoryTest.TestConfig.class })
class JdbcVkHistoryRepositoryTest {

        @Autowired
        private VkHistoryRepository repository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void saveIncrementsCountAndUpdatesTimestamp() {
                VkImageHistoryRecord record = new VkImageHistoryRecord(
                                123L,
                                "https://example.com/a.jpg",
                                "hash-a",
                                Instant.now());

                assertThat(repository.save(record)).isTrue();
                assertThat(repository.count()).isEqualTo(1);
                assertThat(repository.lastSynced()).isNotNull();

                // тот же hash — не увеличиваем количество
                VkImageHistoryRecord duplicate = new VkImageHistoryRecord(
                                124L,
                                "https://example.com/a.jpg",
                                "hash-a",
                                Instant.now());

                assertThat(repository.save(duplicate)).isTrue();
                assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void savePersistsMlMetadata() {
                VkImageHistoryRecord record = new VkImageHistoryRecord(
                                125L,
                                "https://example.com/ml.jpg",
                                "hash-ml",
                                Instant.now());
                record.setMlDecision("SKIP");
                record.setMlScore(0.55);
                record.setMlReason("not suitable");

                repository.save(record);

                Map<String, Object> row = jdbcTemplate.queryForMap(
                                "SELECT ml_decision, ml_score, ml_reason FROM vk_image_history WHERE hash = ?",
                                "hash-ml");
                assertThat(row.get("ML_DECISION")).isEqualTo("SKIP");
                assertThat(((Number) row.get("ML_SCORE")).doubleValue()).isEqualTo(0.55);
                assertThat(row.get("ML_REASON")).isEqualTo("not suitable");
        }

        @Test
        void saveDefaultsUseForTrainingTrueAndAllowsUpdate() {
                VkImageHistoryRecord record = new VkImageHistoryRecord(
                                126L,
                                "https://example.com/train.jpg",
                                "hash-train",
                                Instant.now());
                repository.save(record);

                Map<String, Object> row = jdbcTemplate.queryForMap(
                                "SELECT id, use_for_training FROM vk_image_history WHERE hash = ?",
                                "hash-train");

                Long id = ((Number) row.get("ID")).longValue();
                assertThat((Boolean) row.get("USE_FOR_TRAINING")).isTrue();

                assertThat(repository.updateUseForTraining(id, false)).isTrue();

                Map<String, Object> updated = jdbcTemplate.queryForMap(
                                "SELECT use_for_training FROM vk_image_history WHERE hash = ?",
                                "hash-train");
                assertThat((Boolean) updated.get("USE_FOR_TRAINING")).isFalse();
        }

        @TestConfiguration
        static class TestConfig {

                @Bean
                LogEventsPublisher logEventsPublisher() {
                        // мок, чтобы SpringWebParserApplication спокойно поднялся
                        return Mockito.mock(LogEventsPublisher.class);
                }
        }
}
