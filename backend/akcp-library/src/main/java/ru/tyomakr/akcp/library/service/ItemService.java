package ru.tyomakr.akcp.library.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.core.model.CursorPage;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.ItemCursor;
import ru.tyomakr.akcp.library.persistence.AttachmentRow;
import ru.tyomakr.akcp.library.persistence.ItemRow;
import ru.tyomakr.akcp.library.persistence.ItemTagRow;
import ru.tyomakr.akcp.library.persistence.TagRow;
import ru.tyomakr.akcp.library.repository.AttachmentRepository;
import ru.tyomakr.akcp.library.repository.ItemRepository;
import ru.tyomakr.akcp.library.repository.TagRepository;

@Service
public class ItemService {
  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final ItemRepository itemRepository;
  private final AttachmentRepository attachmentRepository;
  private final TagRepository tagRepository;
  private final TagService tagService;
  private final CursorCodec cursorCodec;

  public ItemService(
      ItemRepository itemRepository,
      AttachmentRepository attachmentRepository,
      TagRepository tagRepository,
      TagService tagService,
      CursorCodec cursorCodec
  ) {
    this.itemRepository = itemRepository;
    this.attachmentRepository = attachmentRepository;
    this.tagRepository = tagRepository;
    this.tagService = tagService;
    this.cursorCodec = cursorCodec;
  }

  public Mono<Item> createItem(CreateItemCommand command) {
    Instant now = Instant.now();
    UUID itemId = UUID.randomUUID();
    ItemRow row = new ItemRow(
        itemId,
        command.title(),
        command.content(),
        command.sourceType().name(),
        command.sourceUrl(),
        now,
        now
    );
    List<AttachmentRow> attachments = mapAttachments(itemId, command.attachments());

    return itemRepository.insert(row)
        .flatMap(saved -> attachmentRepository.insertAll(attachments).thenReturn(saved))
        .flatMap(saved -> tagService.addTags(itemId, command.tags()).thenReturn(saved))
        .flatMap(saved -> assembleSingle(saved));
  }

  public Mono<Item> getItem(UUID id) {
    return itemRepository.findById(id)
        .switchIfEmpty(Mono.error(new ItemNotFoundException(id)))
        .flatMap(this::assembleSingle);
  }

  public Mono<CursorPage<Item>> listItems(ItemQuery query) {
    int limit = normalizeLimit(query.limit());
    ItemCursor cursor = query.cursor() != null ? cursorCodec.decode(query.cursor()) : null;

    return itemRepository.list(query.from(), query.to(), query.tag(), limit + 1, cursor)
        .collectList()
        .flatMap(rows -> {
          boolean hasMore = rows.size() > limit;
          List<ItemRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
          ItemRow last = hasMore && !pageRows.isEmpty()
              ? pageRows.get(pageRows.size() - 1)
              : null;
          String nextCursor = last == null
              ? null
              : cursorCodec.encode(new ItemCursor(last.createdAt(), last.id()));
          return assembleItems(pageRows)
              .map(items -> new CursorPage<>(items, nextCursor));
        });
  }

  public Mono<Item> updateTags(UUID itemId, List<String> add, List<String> remove) {
    Instant now = Instant.now();
    return itemRepository.findById(itemId)
        .switchIfEmpty(Mono.error(new ItemNotFoundException(itemId)))
        .then(tagService.addTags(itemId, add))
        .then(tagService.removeTags(itemId, remove))
        .then(itemRepository.updateUpdatedAt(itemId, now))
        .then(getItem(itemId));
  }

  private Mono<Item> assembleSingle(ItemRow row) {
    return assembleItems(List.of(row))
        .map(items -> items.get(0));
  }

  private Mono<List<Item>> assembleItems(List<ItemRow> rows) {
    if (rows.isEmpty()) {
      return Mono.just(List.of());
    }
    List<UUID> ids = rows.stream().map(ItemRow::id).toList();
    Mono<List<AttachmentRow>> attachmentsMono = attachmentRepository.findByItemIds(ids).collectList();
    Mono<List<ItemTagRow>> tagsMono = tagRepository.findByItemIds(ids).collectList();

    return Mono.zip(attachmentsMono, tagsMono)
        .map(tuple -> {
          List<AttachmentRow> attachments = tuple.getT1();
          List<ItemTagRow> tags = tuple.getT2();

          Map<UUID, List<AttachmentRow>> attachmentsByItem = attachments.stream()
              .collect(Collectors.groupingBy(AttachmentRow::itemId));
          Map<UUID, Set<TagRow>> tagsByItem = tags.stream()
              .collect(Collectors.groupingBy(ItemTagRow::itemId,
                  Collectors.mapping(ItemTagRow::tag, Collectors.toSet())));

          return rows.stream()
              .map(row -> ItemMapper.toItem(
                  row,
                  attachmentsByItem.getOrDefault(row.id(), List.of()),
                  tagsByItem.getOrDefault(row.id(), Set.of())
              ))
              .collect(Collectors.toList());
        });
  }

  private int normalizeLimit(Integer limit) {
    int raw = limit == null ? DEFAULT_LIMIT : limit;
    if (raw <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(raw, MAX_LIMIT);
  }

  private List<AttachmentRow> mapAttachments(UUID itemId, List<CreateItemCommand.CreateAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) {
      return List.of();
    }
    List<AttachmentRow> rows = new ArrayList<>();
    for (CreateItemCommand.CreateAttachment attachment : attachments) {
      rows.add(new AttachmentRow(
          UUID.randomUUID(),
          itemId,
          attachment.type().name(),
          attachment.url(),
          attachment.metadata()
      ));
    }
    return rows;
  }
}
