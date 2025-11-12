package ru.aikr.inet.parser.history.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.aikr.inet.parser.history.model.VkProperties;
import ru.aikr.inet.parser.vk.VkApiProperties;

@Configuration
@EnableConfigurationProperties({VkProperties.class, VkApiProperties.class})
public class HistoryConfig {
}
