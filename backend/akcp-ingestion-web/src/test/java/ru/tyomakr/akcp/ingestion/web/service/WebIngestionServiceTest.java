package ru.tyomakr.akcp.ingestion.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.handler.codec.DecoderException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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

  @ParameterizedTest
  @ValueSource(strings = {
      "http://127.0.0.1/",
      "http://localhost/",
      "http://[::1]/",
      "http://10.0.0.1/",
      "http://172.16.0.1/",
      "http://192.168.0.1/",
      "http://169.254.169.254/latest/meta-data/"
  })
  void parseShouldRejectLoopbackPrivateAndLinkLocalTargetsBeforeNetwork(String url) {
    AtomicInteger requests = new AtomicInteger();
    ExchangeFunction noNetwork = request -> {
      requests.incrementAndGet();
      return Mono.error(new AssertionError("SSRF contract reached the transport"));
    };
    WebIngestionService service = serviceWithExchangeFunction(noNetwork);

    StepVerifier.create(service.parse(url))
        .expectError(IllegalArgumentException.class)
        .verify();
    assertThat(requests).hasValue(0);
  }

  @Test
  void parseRejectsRedirectToLoopbackBeforeFollowingIt() {
    AtomicInteger requests = new AtomicInteger();
    ExchangeFunction redirect = request -> {
      requests.incrementAndGet();
      return Mono.just(ClientResponse.create(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, "http://127.0.0.1/internal")
          .build());
    };
    WebIngestionService service = serviceWithExchangeFunction(redirect);

    StepVerifier.create(service.parse("https://example.com/page"))
        .expectError(IllegalArgumentException.class)
        .verify();
    assertThat(requests).hasValue(1);
  }

  @Test
  void parseMapsTlsCertificateErrorsToBadGateway() throws Exception {
    Throwable tls = new SSLHandshakeException(
        "PKIX path building failed: unable to find valid certification path to requested target");
    WebClientRequestException requestException = new WebClientRequestException(
        new DecoderException(tls),
        HttpMethod.GET,
        URI.create("https://example.com/page"),
        HttpHeaders.EMPTY
    );
    ExchangeFunction exchangeFunction = request -> Mono.error(requestException);
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
    RequestDelayService delayService = uri -> Mono.empty();
    UserAgentService userAgentService = () -> "Test UA";
    WebIngestionService service =
        new WebIngestionService(
            builder,
            testUrlSafetyPolicy(),
            mock(ItemService.class),
            new WebImageParser(),
            delayService,
            userAgentService,
            new SimpleMeterRegistry()
        );

    StepVerifier.create(service.parse("https://example.com/page"))
        .expectErrorSatisfies(error -> {
          assertThat(error).isInstanceOf(WebIngestionException.class);
          WebIngestionException mapped = (WebIngestionException) error;
          assertThat(mapped.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
          assertThat(mapped.getMessage()).contains("TLS certificate validation failed");
        })
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
    RequestDelayService delayService = uri -> Mono.empty();
    UserAgentService userAgentService = () -> "Test UA";
    return new WebIngestionService(
        builder,
        testUrlSafetyPolicy(),
        itemService,
        new WebImageParser(),
        delayService,
        userAgentService,
        new SimpleMeterRegistry()
    );
  }

  private WebIngestionService serviceWithExchangeFunction(ExchangeFunction exchangeFunction) {
    return new WebIngestionService(
        WebClient.builder().exchangeFunction(exchangeFunction),
        testUrlSafetyPolicy(),
        mock(ItemService.class),
        new WebImageParser(),
        uri -> Mono.empty(),
        () -> "Test UA",
        new SimpleMeterRegistry()
    );
  }

  private UrlSafetyPolicy testUrlSafetyPolicy() {
    return new UrlSafetyPolicy(host -> {
      if ("example.com".equalsIgnoreCase(host)) {
        return new InetAddress[]{InetAddress.getByName("93.184.216.34")};
      }
      return InetAddress.getAllByName(host);
    });
  }
}
