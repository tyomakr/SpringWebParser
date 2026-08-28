package ru.tyomakr.akcp.ingestion.web.service;

import java.net.URI;
import reactor.core.publisher.Mono;

public interface RequestDelayService {
  Mono<Void> maybeDelay(URI uri);
}
