package ru.tyomakr.akcp.ingestion.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.tyomakr.akcp.core.model.Item;
import ru.tyomakr.akcp.core.model.SourceRef;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.library.service.CreateItemCommand;
import ru.tyomakr.akcp.library.service.ItemService;

class WebIngestionServiceTest {
  @Test
  void parseReturnsAttachments() {
    String html = "<html><head><title>Title</title></head><body>"
        + "<img src=\"/a.jpg\">"
        + "<img data-src=\"https://example.com/b.jpg\">"
        + "</body></html>";
    WebIngestionService service = serviceWithHtml(html);

    StepVerifier.create(service.parse("https://example.com/page"))
        .assertNext(result -> {
          assertThat(result.title()).isEqualTo("Title");
          assertThat(result.attachments()).hasSize(2);
          assertThat(result.attachments().get(0).url()).isEqualTo("https://example.com/a.jpg");
          assertThat(result.attachments().get(1).url()).isEqualTo("https://example.com/b.jpg");
        })
        .verifyComplete();
  }

  @Test
  void parseAndMaybeCreateCreatesItem() {
    String html = "<html><head><title>Title</title></head><body>"
        + "<img src=\"/a.jpg\">"
        + "</body></html>";
    ItemService itemService = mock(ItemService.class);
    UUID itemId = UUID.randomUUID();
    Item created = new Item(
        itemId,
        "Title",
        null,
        new SourceRef(SourceType.WEB, "https://example.com/page"),
        List.of(),
        Set.of(),
        Instant.now(),
        Instant.now()
    );
    when(itemService.createItem(any(CreateItemCommand.class))).thenReturn(Mono.just(created));

    WebIngestionService service = serviceWithHtml(html, itemService);

    StepVerifier.create(service.parseAndMaybeCreate("https://example.com/page", true))
        .assertNext(result -> assertThat(result.createdItemId()).isEqualTo(itemId))
        .verifyComplete();
  }

  @Test
  void parseRejectsInvalidUrlScheme() {
    WebIngestionService service = serviceWithHtml("<html></html>");

    StepVerifier.create(service.parse("ftp://example.com/file"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  private WebIngestionService serviceWithHtml(String html) {
    return serviceWithHtml(html, mock(ItemService.class));
  }

  private WebIngestionService serviceWithHtml(String html, ItemService itemService) {
    ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
        .body(html)
        .build());
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
    return new WebIngestionService(builder, itemService, new WebImageParser());
  }
}
