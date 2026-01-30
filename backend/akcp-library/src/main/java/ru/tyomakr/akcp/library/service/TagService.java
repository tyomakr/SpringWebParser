package ru.tyomakr.akcp.library.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.persistence.TagRow;
import ru.tyomakr.akcp.library.repository.TagRepository;

@Service
public class TagService {
  private final TagRepository tagRepository;

  public TagService(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  public Mono<List<TagRow>> ensureTags(List<String> names) {
    List<String> normalized = normalize(names);
    if (normalized.isEmpty()) {
      return Mono.just(List.of());
    }
    return Flux.fromIterable(normalized)
        .concatMap(name -> tagRepository.findByName(name)
            .switchIfEmpty(tagRepository.insert(new TagRow(UUID.randomUUID(), name))
                .switchIfEmpty(tagRepository.findByName(name))))
        .collectList();
  }

  public Mono<Void> addTags(UUID itemId, List<String> names) {
    return ensureTags(names)
        .flatMap(tags -> tagRepository.insertItemTags(itemId, tags.stream().map(TagRow::id).toList()));
  }

  public Mono<Void> removeTags(UUID itemId, List<String> names) {
    List<String> normalized = normalize(names);
    if (normalized.isEmpty()) {
      return Mono.empty();
    }
    return Flux.fromIterable(normalized)
        .concatMap(tagRepository::findByName)
        .map(TagRow::id)
        .collectList()
        .flatMap(tagIds -> tagRepository.deleteItemTags(itemId, tagIds));
  }

  private List<String> normalize(List<String> names) {
    if (names == null) {
      return List.of();
    }
    return names.stream()
        .filter(name -> name != null && !name.isBlank())
        .map(String::trim)
        .distinct()
        .collect(Collectors.toList());
  }
}
