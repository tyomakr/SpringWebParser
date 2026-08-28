package ru.tyomakr.akcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({AiProperties.class})
public class AppConfig {
  @Bean
  public WebClient.Builder webClientBuilder(
      @Value("${akcp.http.max-in-memory-bytes:2097152}") int maxInMemoryBytes) {
    ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemoryBytes))
        .build();
    return WebClient.builder().exchangeStrategies(strategies);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CorsWebFilter corsWebFilter(@Value("${akcp.cors.allowed-origins:http://localhost:3333}") String originsRaw) {
    CorsConfiguration config = new CorsConfiguration();
    List<String> origins = Arrays.stream(originsRaw.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toList());
    if (origins.contains("*")) {
      config.setAllowedOriginPatterns(List.of("*"));
    } else {
      config.setAllowedOrigins(origins);
    }
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }
}
