package ru.tyomakr.akcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ru.tyomakr.akcp.core.ai.EmbeddingMode;

@ConfigurationProperties(prefix = "akcp.ai")
public record AiProperties(EmbeddingMode mode, Remote remote) {
  public record Remote(String url) {
  }
}
