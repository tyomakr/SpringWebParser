package ru.tyomakr.akcp.ingestion.web.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;
import ru.tyomakr.akcp.library.service.CreateItemCommand;
import ru.tyomakr.akcp.library.service.ItemService;

@Service
public class WebIngestionService {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_REDIRECTS = 5;

  private final WebClient webClient;
  private final UrlSafetyPolicy urlSafetyPolicy;
  private final ItemService itemService;
  private final WebImageParser webImageParser;
  private final RequestDelayService requestDelayService;
  private final UserAgentService userAgentService;
  private final MeterRegistry meterRegistry;

  public WebIngestionService(WebClient.Builder webClientBuilder,
                             UrlSafetyPolicy urlSafetyPolicy,
                             ItemService itemService,
                             WebImageParser webImageParser,
                             RequestDelayService requestDelayService,
                             UserAgentService userAgentService,
                             MeterRegistry meterRegistry) {
    HttpClient httpClient = HttpClient.create()
        .resolvedAddressesSelector((configuration, addresses) ->
            urlSafetyPolicy.requirePublicSocketAddresses(addresses));
    this.webClient = webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
    this.urlSafetyPolicy = urlSafetyPolicy;
    this.itemService = itemService;
    this.webImageParser = webImageParser;
    this.requestDelayService = requestDelayService;
    this.userAgentService = userAgentService;
    this.meterRegistry = meterRegistry;
  }

  public Mono<WebParseResult> parse(String url) {
    return urlSafetyPolicy.validate(url)
        .flatMap(uri -> fetch(uri, 0)
            .flatMap(page -> parseHtml(page.uri().toString(), page.html()))
            .onErrorMap(error -> mapRemoteError(uri, error)))
        .doOnSuccess(result -> meterRegistry.counter(
            "akcp.ingestion.web.parse.total",
            "result",
            "success"
        ).increment())
        .doOnError(error -> meterRegistry.counter(
            "akcp.ingestion.web.parse.total",
            "result",
            "error",
            "errorType",
            classifyError(error)
        ).increment());
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
        .map(attachment -> new CreateItemCommand.CreateAttachment(
            AttachmentTypeResolver.resolve(attachment.url()),
            attachment.url(),
            null))
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

  private Mono<FetchedPage> fetch(URI uri, int redirectCount) {
    return requestDelayService.maybeDelay(uri)
        .then(webClient.get()
            .uri(uri)
            .headers(headers -> headers.set(HttpHeaders.USER_AGENT, userAgentService.getRandomUserAgent()))
            .exchangeToMono(response -> {
              if (response.statusCode().is3xxRedirection()) {
                if (redirectCount >= MAX_REDIRECTS) {
                  return Mono.error(new IllegalArgumentException("Too many URL redirects"));
                }
                URI location = response.headers().asHttpHeaders().getLocation();
                if (location == null) {
                  return Mono.error(new IllegalArgumentException("URL redirect is missing Location"));
                }
                URI redirectUri = uri.resolve(location);
                return response.releaseBody()
                    .then(urlSafetyPolicy.validate(redirectUri.toString()))
                    .flatMap(validated -> fetch(validated, redirectCount + 1));
              }
              if (!response.statusCode().is2xxSuccessful()) {
                return response.createException().flatMap(Mono::error);
              }
              return response.bodyToMono(String.class)
                  .map(html -> new FetchedPage(uri, html));
            })
            .timeout(REQUEST_TIMEOUT));
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

  private record FetchedPage(URI uri, String html) {
  }

  private Throwable mapRemoteError(URI uri, Throwable error) {
    if (error instanceof WebIngestionException || error instanceof IllegalArgumentException) {
      return error;
    }
    if (error instanceof java.util.concurrent.TimeoutException) {
      return new WebIngestionException(
          HttpStatus.GATEWAY_TIMEOUT,
          "Failed to fetch URL: request timed out",
          error
      );
    }
    if (error instanceof WebClientResponseException responseException) {
      String statusText = responseException.getStatusCode() + " " + responseException.getStatusText();
      return new WebIngestionException(
          HttpStatus.BAD_GATEWAY,
          "Failed to fetch URL: upstream responded with " + statusText,
          error
      );
    }
    if (error instanceof WebClientRequestException requestException) {
      return mapNetworkError(uri, requestException);
    }
    return new WebIngestionException(
        HttpStatus.BAD_GATEWAY,
        "Failed to fetch URL: unexpected network error",
        error
    );
  }

  private WebIngestionException mapNetworkError(URI uri, WebClientRequestException ex) {
    Throwable root = rootCause(ex);
    String host = uri == null ? "unknown host" : Objects.toString(uri.getHost(), "unknown host");

    if (root instanceof UnknownHostException) {
      return new WebIngestionException(
          HttpStatus.BAD_GATEWAY,
          "Failed to fetch URL: host is not reachable (" + host + ")",
          ex
      );
    }
    if (root instanceof SSLException || containsCertificateError(ex)) {
      return new WebIngestionException(
          HttpStatus.BAD_GATEWAY,
          "Failed to fetch URL: TLS certificate validation failed (" + host + ")",
          ex
      );
    }
    return new WebIngestionException(
        HttpStatus.BAD_GATEWAY,
        "Failed to fetch URL: network request failed (" + host + ")",
        ex
    );
  }

  private boolean containsCertificateError(Throwable throwable) {
    Throwable current = throwable;
    while (current != null && current.getCause() != current) {
      String message = Objects.toString(current.getMessage(), "").toLowerCase(Locale.ROOT);
      String type = current.getClass().getName().toLowerCase(Locale.ROOT);
      if (message.contains("pkix")
          || message.contains("certificate")
          || message.contains("certification path")
          || message.contains("suncertpathbuilderexception")
          || type.contains("sslhandshakeexception")
          || type.contains("sslexception")
          || type.contains("certpathbuilder")
          || type.contains("validatorexception")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private String classifyError(Throwable error) {
    if (error instanceof IllegalArgumentException) {
      return "validation";
    }
    if (error instanceof WebIngestionException webEx) {
      if (webEx.status() == HttpStatus.GATEWAY_TIMEOUT) {
        return "timeout";
      }
      String message = Objects.toString(webEx.getMessage(), "").toLowerCase(Locale.ROOT);
      if (message.contains("tls")) {
        return "tls";
      }
      if (message.contains("upstream")) {
        return "upstream";
      }
      return "network";
    }
    return error.getClass().getSimpleName().toLowerCase(Locale.ROOT);
  }
}
