package ru.tyomakr.akcp.ingestion.web.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.time.Duration;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;
import ru.tyomakr.akcp.library.service.CreateItemCommand;
import ru.tyomakr.akcp.library.service.ItemService;

@Service
public class WebIngestionService {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final ItemService itemService;
  private final WebImageParser webImageParser;

  public WebIngestionService(WebClient.Builder webClientBuilder, ItemService itemService, WebImageParser webImageParser) {
    this.webClient = webClientBuilder.build();
    this.itemService = itemService;
    this.webImageParser = webImageParser;
  }

  public Mono<WebParseResult> parse(String url) {
    return validateUrl(url).then(webClient.get()
        .uri(url)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(REQUEST_TIMEOUT)
        .flatMap(html -> parseHtml(url, html)));
  }

  public Mono<WebParseResult> parseAndMaybeCreate(String url, boolean createItem) {
    return parse(url)
        .flatMap(result -> {
          if (!createItem) {
            return Mono.just(result);
          }
          return createItemFromResult(url, result)
              .map(itemId -> result.withCreatedItemId(itemId));
        });
  }

  private Mono<WebParseResult> parseHtml(String baseUrl, String html) {
    return Mono.fromCallable(() -> webImageParser.parse(baseUrl, html))
        .subscribeOn(Schedulers.boundedElastic())
        .map(result -> result);
  }

  private Mono<java.util.UUID> createItemFromResult(String url, WebParseResult result) {
    String title = result.title();
    if (title == null || title.isBlank()) {
      title = url;
    }
    List<CreateItemCommand.CreateAttachment> attachments = result.attachments().stream()
        .map(attachment -> new CreateItemCommand.CreateAttachment(AttachmentType.IMAGE, attachment.url(), null))
        .toList();
    CreateItemCommand command = new CreateItemCommand(
        title,
        null,
        SourceType.WEB,
        url,
        attachments,
        List.of()
    );
    return itemService.createItem(command).map(item -> item.id());
  }

  private Mono<Void> validateUrl(String url) {
    if (url == null || url.isBlank()) {
      return Mono.error(new IllegalArgumentException("URL is required"));
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      return Mono.error(new IllegalArgumentException("Invalid URL", ex));
    }
    String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      return Mono.error(new IllegalArgumentException("Only http/https URLs are supported"));
    }
    return Mono.empty();
  }

  public record WebParseResult(
      String url,
      String title,
      List<ParsedAttachment> attachments,
      java.util.UUID createdItemId
  ) {
    public WebParseResult withCreatedItemId(java.util.UUID createdItemId) {
      return new WebParseResult(url, title, attachments, createdItemId);
    }
  }
}
