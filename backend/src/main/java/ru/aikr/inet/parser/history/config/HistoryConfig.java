package ru.aikr.inet.parser.history.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.aikr.inet.parser.history.model.VkProperties;

@Configuration
@EnableConfigurationProperties(VkProperties.class)
public class HistoryConfig {
}
