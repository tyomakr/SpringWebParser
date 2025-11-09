package ru.aikr.inet.parser.history;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import ru.aikr.inet.parser.logging.LogEventsPublisher;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcVkHistoryRepository.class, JdbcVkHistoryRepositoryTest.TestConfig.class})
class JdbcVkHistoryRepositoryTest {

    @Autowired
    private VkHistoryRepository repository;

    @Test
    void saveIncrementsCountAndUpdatesTimestamp() {
        VkImageHistoryRecord record = new VkImageHistoryRecord(
                123L,
                "https://example.com/a.jpg",
                "hash-a",
                Instant.now()
        );

        assertThat(repository.save(record)).isTrue();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.lastSynced()).isNotNull();

        // тот же hash — не увеличиваем количество
        VkImageHistoryRecord duplicate = new VkImageHistoryRecord(
                124L,
                "https://example.com/a.jpg",
                "hash-a",
                Instant.now()
        );

        assertThat(repository.save(duplicate)).isTrue();
        assertThat(repository.count()).isEqualTo(1);
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