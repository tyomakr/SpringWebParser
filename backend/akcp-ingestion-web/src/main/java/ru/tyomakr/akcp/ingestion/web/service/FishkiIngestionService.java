package ru.tyomakr.akcp.ingestion.web.service;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import ru.tyomakr.akcp.core.model.AttachmentType;
import ru.tyomakr.akcp.core.model.SourceType;
import ru.tyomakr.akcp.ingestion.web.dto.FishkiParseResult;
import ru.tyomakr.akcp.ingestion.web.dto.ParsedAttachment;
import ru.tyomakr.akcp.library.service.CreateItemCommand;
import ru.tyomakr.akcp.library.service.ItemService;

@Service
public class FishkiIngestionService {
  private static final Logger log = LoggerFactory.getLogger(FishkiIngestionService.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final String FALLBACK_UA =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private final WebClient webClient;
  private final ItemService itemService;
  private final FishkiImageParser fishkiImageParser;
  private final RequestDelayService requestDelayService;
  private final UserAgentService userAgentService;
  private final UrlSafetyPolicy urlSafetyPolicy;

  @Value("${akcp.ingestion.fishki.base-url:http://fishki.net/mix/}")
  private String baseUrl;

  @Value("${akcp.ingestion.fishki.container-selector:div.embed-image-single}")
  private String containerSelector;

  public FishkiIngestionService(WebClient.Builder webClientBuilder,
                                ItemService itemService,
                                FishkiImageParser fishkiImageParser,
                                RequestDelayService requestDelayService,
                                UserAgentService userAgentService,
                                UrlSafetyPolicy urlSafetyPolicy) {
    HttpClient httpClient = HttpClient.create()
        .resolvedAddressesSelector((configuration, addresses) ->
            urlSafetyPolicy.requirePublicSocketAddresses(addresses));
    this.webClient = webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
    this.itemService = itemService;
    this.fishkiImageParser = fishkiImageParser;
    this.requestDelayService = requestDelayService;
    this.userAgentService = userAgentService;
    this.urlSafetyPolicy = urlSafetyPolicy;
  }

  public Mono<FishkiParseResult> parseRange(int pageFrom, int pageTo, boolean createItem) {
    int normalizedFrom = Math.max(1, pageFrom);
    int normalizedTo = Math.max(normalizedFrom, pageTo);
    int pageCount = normalizedTo - normalizedFrom + 1;

    log.info("Fishki parse request pages {}-{} (selector={})", normalizedFrom, normalizedTo, containerSelector);

    return Flux.range(normalizedFrom, pageCount)
        .concatMap(this::fetchPageAttachments)
        .collectList()
        .flatMap(pages -> {
          Set<String> deduped = new LinkedHashSet<>();
          for (List<ParsedAttachment> page : pages) {
            page.forEach(attachment -> deduped.add(attachment.url()));
          }
          List<ParsedAttachment> attachments = deduped.stream().map(ParsedAttachment::new).toList();
          FishkiParseResult result = new FishkiParseResult(baseUrl, normalizedFrom, normalizedTo, pageCount, attachments, null);
          log.info("Fishki parse complete: {} attachments (pages {}-{})", attachments.size(), normalizedFrom, normalizedTo);
          if (!createItem) {
            return Mono.just(result);
          }
          return createItemFromResult(result)
              .map(result::withCreatedItemId);
        });
  }

  private Mono<List<ParsedAttachment>> fetchPageAttachments(int page) {
    String url = buildPageUrl(page);
    URI uri = URI.create(url);
    String userAgent = userAgentService.getRandomUserAgent();
    return urlSafetyPolicy.validate(uri.toString())
        .flatMap(validated -> requestDelayService.maybeDelay(validated)
            .then(fetchHtml(validated, userAgent, 2)))
        .flatMap(response -> parseWithDiagnostics(page, response)
            .flatMap(result -> {
              if (!result.attachments().isEmpty()) {
                return Mono.just(result.attachments());
              }
              if (shouldRetry(response)) {
                log.info("Fishki page {} retrying with fallback UA", page);
                return fetchHtml(response.uri(), FALLBACK_UA, 2)
                    .flatMap(fallback -> parseWithDiagnostics(page, fallback))
                    .map(ParseResult::attachments);
              }
              return Mono.just(result.attachments());
            }))
        .onErrorResume(ex -> {
          log.warn("Fishki parse failed for page {}: {}", page, ex.getMessage());
          return Mono.just(List.of());
        });
  }

  private Mono<ParseResult> parseWithDiagnostics(int page, HtmlResponse response) {
    return parseHtml(response.uri().toString(), response.body())
        .map(attachments -> {
          if (attachments.isEmpty()) {
            String body = response.body();
            int length = body == null ? 0 : body.length();
            boolean hasEmbed = body != null && body.contains("embed-image-single");
            boolean hasTiny = body != null && body.contains("tiny__info__img");
            boolean hasCaptcha = body != null && (body.contains("captcha") || body.contains("cloudflare"));
            log.warn("Fishki page {} returned 0 images (status={}, type={}, bytes={}, embed={}, tiny={}, captcha={})",
                page, response.statusCode(), response.contentType(), length, hasEmbed, hasTiny, hasCaptcha);
            if (length > 0) {
              int end = Math.min(200, length);
              log.warn("Fishki page {} snippet: {}", page, body.substring(0, end).replaceAll("\\s+", " "));
            }
          }
          return new ParseResult(response, attachments);
        });
  }

  private Mono<List<ParsedAttachment>> parseHtml(String baseUrl, String html) {
    return Mono.fromCallable(() -> fishkiImageParser.parse(baseUrl, html, containerSelector))
        .subscribeOn(Schedulers.boundedElastic());
  }

  private Mono<java.util.UUID> createItemFromResult(FishkiParseResult result) {
    List<CreateItemCommand.CreateAttachment> attachments = result.attachments().stream()
        .map(attachment -> new CreateItemCommand.CreateAttachment(
            AttachmentTypeResolver.resolve(attachment.url()),
            attachment.url(),
            null))
        .toList();
    String title = String.format(Locale.ROOT, "Fishki %d-%d", result.pageFrom(), result.pageTo());
    CreateItemCommand command = new CreateItemCommand(
        title,
        null,
        SourceType.WEB,
        baseUrl,
        attachments,
        List.of()
    );
    return itemService.createItem(command).map(item -> item.id());
  }

  private String buildPageUrl(int page) {
    String trimmed = baseUrl == null ? "" : baseUrl.trim();
    if (trimmed.isEmpty()) {
      trimmed = "https://fishki.net/mix/";
    }
    if (!trimmed.endsWith("/")) {
      trimmed = trimmed + "/";
    }
    return trimmed + page + "/";
  }

  private Mono<HtmlResponse> fetchHtml(URI uri, String userAgent, int redirectsLeft) {
    return webClient.get()
        .uri(uri)
        .headers(headers -> {
          headers.set(HttpHeaders.USER_AGENT, userAgent);
          headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
          headers.set(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7");
          headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
          headers.set(HttpHeaders.ACCEPT_ENCODING, "identity");
          headers.set(HttpHeaders.REFERER, baseUrl);
        })
        .exchangeToMono(response -> {
          HttpStatusCode status = response.statusCode();
          String contentType = response.headers().contentType().map(MediaType::toString).orElse("n/a");
          if (status.isError()) {
            log.warn("Fishki response status {} for {}", status.value(), uri);
          }
          if (status.is3xxRedirection()) {
            String location = response.headers().header(HttpHeaders.LOCATION).stream().findFirst().orElse(null);
            return response.releaseBody().then(Mono.defer(() -> {
              if (location == null || redirectsLeft <= 0) {
                log.warn("Fishki redirect without location (status={})", status.value());
                return Mono.just(new HtmlResponse(uri, status.value(), contentType, ""));
              }
              URI next = uri.resolve(location);
              return urlSafetyPolicy.validate(next.toString())
                  .doOnNext(validated -> log.info("Fishki redirect to host {}", validated.getHost()))
                  .flatMap(validated -> fetchHtml(validated, userAgent, redirectsLeft - 1));
            }));
          }
          return response.bodyToMono(String.class)
              .timeout(REQUEST_TIMEOUT)
              .map(body -> new HtmlResponse(uri, status.value(), contentType, body));
        });
  }

  private boolean shouldRetry(HtmlResponse response) {
    if (response.statusCode() >= 400) {
      return true;
    }
    String body = response.body();
    if (body == null || body.isBlank()) {
      return true;
    }
    return !(body.contains("embed-image-single") || body.contains("tiny__info__img"));
  }

  private record HtmlResponse(URI uri, int statusCode, String contentType, String body) {}

  private record ParseResult(HtmlResponse response, List<ParsedAttachment> attachments) {}
}
