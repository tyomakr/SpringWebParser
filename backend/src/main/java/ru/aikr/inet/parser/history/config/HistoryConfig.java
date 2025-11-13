package ru.aikr.inet.parser.history.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.aikr.inet.parser.history.model.VkProperties;
import ru.aikr.inet.parser.history.model.VkSyncProperties;
import ru.aikr.inet.parser.vk.VkApiProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({VkProperties.class, VkApiProperties.class, VkSyncProperties.class})
public class HistoryConfig {
}
