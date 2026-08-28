package ru.tyomakr.akcp.publishing.vk.service;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface VkPublisher {
  Mono<Void> publish(UUID itemId);
}
