package ru.tyomakr.akcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({AiProperties.class})
public class AppConfig {
  @Bean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }
}
