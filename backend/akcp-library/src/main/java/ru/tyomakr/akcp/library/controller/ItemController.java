package ru.tyomakr.akcp.library.controller;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.library.dto.CreateItemRequest;
import ru.tyomakr.akcp.library.dto.ItemDtoMapper;
import ru.tyomakr.akcp.library.dto.ItemListResponse;
import ru.tyomakr.akcp.library.dto.ItemResponse;
import ru.tyomakr.akcp.library.dto.TagPatchRequest;
import ru.tyomakr.akcp.library.service.CreateItemCommand;
import ru.tyomakr.akcp.library.service.ItemQuery;
import ru.tyomakr.akcp.library.service.ItemService;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService itemService;

  public ItemController(ItemService itemService) {
    this.itemService = itemService;
  }

  @PostMapping
  public Mono<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
    CreateItemCommand command = new CreateItemCommand(
        request.title(),
        request.content(),
        parseSourceType(request.sourceType()),
        request.sourceUrl(),
        mapAttachments(request),
        request.tags()
    );
    return itemService.createItem(command)
        .map(ItemDtoMapper::toResponse);
  }

  @GetMapping
  public Mono<ItemListResponse> list(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String tag
  ) {
    ItemQuery query = new ItemQuery(from, to, tag, limit, cursor);
    return itemService.listItems(query)
        .map(page -> ItemDtoMapper.toListResponse(page.items(), page.nextCursor()));
  }

  @GetMapping("/{id}")
  public Mono<ItemResponse> get(@PathVariable UUID id) {
    return itemService.getItem(id).map(ItemDtoMapper::toResponse);
  }

  @PatchMapping("/{id}/tags")
  public Mono<ItemResponse> updateTags(@PathVariable UUID id, @RequestBody TagPatchRequest request) {
    return itemService.updateTags(id, safeList(request.add()), safeList(request.remove()))
        .map(ItemDtoMapper::toResponse);
  }

  private SourceType parseSourceType(String raw) {
    if (raw == null || raw.isBlank()) {
      return SourceType.MANUAL;
    }
    return SourceType.valueOf(raw.trim().toUpperCase());
  }

  private List<CreateItemCommand.CreateAttachment> mapAttachments(CreateItemRequest request) {
    if (request.attachments() == null) {
      return List.of();
    }
    return request.attachments().stream()
        .map(attachment -> new CreateItemCommand.CreateAttachment(
            AttachmentType.valueOf(attachment.type().trim().toUpperCase()),
            attachment.url(),
            attachment.metadata()
        ))
        .toList();
  }

  private List<String> safeList(List<String> values) {
    return values == null ? List.of() : values;
  }
}
