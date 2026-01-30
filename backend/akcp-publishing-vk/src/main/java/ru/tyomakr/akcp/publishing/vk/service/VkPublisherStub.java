package ru.tyomakr.akcp.publishing.vk.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class VkPublisherStub implements VkPublisher {
  private static final Logger log = LoggerFactory.getLogger(VkPublisherStub.class);

  private final String accessToken;

  public VkPublisherStub(@Value("${akcp.vk.access-token:}") String accessToken) {
    this.accessToken = accessToken;
  }

  @Override
  public Mono<Void> publish(UUID itemId) {
    if (accessToken == null || accessToken.isBlank()) {
      log.info("VK publish stub: no token configured, skipping item {}", itemId);
      return Mono.empty();
    }
    log.info("VK publish stub: token provided, pretending to publish item {}", itemId);
    return Mono.empty();
  }
}
