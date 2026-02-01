package ru.tyomakr.akcp.ingestion.web.service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RequestDelayServiceImpl implements RequestDelayService {
  @Value("${akcp.ingestion.web.delay.enabled:true}")
  private boolean enabled;

  @Value("${akcp.ingestion.web.delay.min-ms:1500}")
  private long minDelayMs;

  @Value("${akcp.ingestion.web.delay.max-ms:4000}")
  private long maxDelayMs;

  @Value("${akcp.ingestion.web.delay.hosts:fishki.net}")
  private String hostsRaw;

  private List<String> hosts;

  @PostConstruct
  void init() {
    hosts = Arrays.stream(hostsRaw.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .collect(Collectors.toList());
  }

  @Override
  public Mono<Void> maybeDelay(URI uri) {
    if (!enabled) {
      return Mono.empty();
    }
    String host = uri.getHost();
    if (host == null || hosts.isEmpty()) {
      return Mono.empty();
    }
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (!matchesHost(normalizedHost)) {
      return Mono.empty();
    }
    long min = Math.max(0L, minDelayMs);
    long max = Math.max(min, maxDelayMs);
    if (max == 0L) {
      return Mono.empty();
    }
    long delay = min;
    if (max > min) {
      delay = min + ThreadLocalRandom.current().nextLong(max - min + 1);
    }
    return Mono.delay(Duration.ofMillis(delay)).then();
  }

  private boolean matchesHost(String host) {
    for (String entry : hosts) {
      if (host.equals(entry) || host.endsWith("." + entry)) {
        return true;
      }
    }
    return false;
  }
}
