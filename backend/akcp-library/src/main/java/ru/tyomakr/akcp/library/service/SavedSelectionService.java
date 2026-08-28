package ru.tyomakr.akcp.library.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.SavedSelection;
import ru.tyomakr.akcp.library.config.SelectionsProperties;
import ru.tyomakr.akcp.library.dto.SavedSelectionRequest;
import ru.tyomakr.akcp.library.repository.SavedSelectionRepository;

@Service
public class SavedSelectionService {
  private final SavedSelectionRepository repository;
  private final SelectionsProperties properties;

  public SavedSelectionService(SavedSelectionRepository repository, SelectionsProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public Mono<SavedSelection> save(String username, SavedSelectionRequest request) {
    if (username == null || username.isBlank()) {
      return Mono.error(new IllegalArgumentException("User is not authenticated"));
    }
    if (request == null || request.itemId() == null) {
      return Mono.error(new IllegalArgumentException("itemId is required"));
    }
    if (request.attachmentIds() == null || request.attachmentIds().isEmpty()) {
      return Mono.error(new IllegalArgumentException("attachmentIds is required"));
    }
    if (request.attachmentIds().stream().anyMatch(id -> id == null)) {
      return Mono.error(new IllegalArgumentException("attachmentIds must not contain null"));
    }

    List<UUID> attachmentIds = List.copyOf(request.attachmentIds());
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.getTtlHours(), ChronoUnit.HOURS);
    SavedSelection selection = new SavedSelection(
        UUID.randomUUID(),
        username,
        request.itemId(),
        attachmentIds,
        request.target(),
        now,
        expiresAt
    );
    return repository.save(selection, serializeIds(attachmentIds)).thenReturn(selection);
  }

  public Flux<SavedSelection> list(String username) {
    if (username == null || username.isBlank()) {
      return Flux.error(new IllegalArgumentException("User is not authenticated"));
    }
    return repository.findByUser(username, Instant.now()).map(this::fromRow);
  }

  public Mono<Void> delete(String username, UUID id) {
    if (username == null || username.isBlank()) {
      return Mono.error(new IllegalArgumentException("User is not authenticated"));
    }
    if (id == null) {
      return Mono.error(new IllegalArgumentException("id is required"));
    }
    return repository.deleteById(id, username);
  }

  public Mono<Void> cleanupExpired() {
    return repository.deleteExpired(Instant.now());
  }

  private SavedSelection fromRow(SavedSelectionRepository.SavedSelectionRow row) {
    return new SavedSelection(
        row.id(),
        row.username(),
        row.itemId(),
        deserializeIds(row.attachmentIdsText()),
        row.target(),
        row.createdAt(),
        row.expiresAt()
    );
  }

  private String serializeIds(List<UUID> ids) {
    return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
  }

  private List<UUID> deserializeIds(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(text.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(UUID::fromString)
        .toList();
  }
}
