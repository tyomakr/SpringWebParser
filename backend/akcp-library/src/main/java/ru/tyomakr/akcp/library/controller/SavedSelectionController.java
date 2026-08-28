package ru.tyomakr.akcp.library.controller;

import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.library.dto.SavedSelectionListResponse;
import ru.tyomakr.akcp.library.dto.SavedSelectionRequest;
import ru.tyomakr.akcp.library.dto.SavedSelectionResponse;
import ru.tyomakr.akcp.library.service.SavedSelectionService;

@RestController
@RequestMapping("/api/selections")
public class SavedSelectionController {
  private final SavedSelectionService service;

  public SavedSelectionController(SavedSelectionService service) {
    this.service = service;
  }

  @PostMapping
  public Mono<SavedSelectionResponse> save(
      @RequestBody SavedSelectionRequest request,
      Principal principal
  ) {
    String username = principal == null ? null : principal.getName();
    return service.save(username, request).map(SavedSelectionResponse::from);
  }

  @GetMapping
  public Mono<SavedSelectionListResponse> list(Principal principal) {
    String username = principal == null ? null : principal.getName();
    return service.list(username)
        .map(SavedSelectionResponse::from)
        .collectList()
        .map(SavedSelectionListResponse::new);
  }

  @DeleteMapping("/{id}")
  public Mono<Void> delete(@PathVariable UUID id, Principal principal) {
    String username = principal == null ? null : principal.getName();
    return service.delete(username, id);
  }
}
