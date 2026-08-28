package ru.tyomakr.akcp.auth.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class BearerTokenConverter implements ServerAuthenticationConverter {
  @Override
  public Mono<Authentication> convert(ServerWebExchange exchange) {
    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      return Mono.empty();
    }
    String token = header.substring(7);
    return Mono.just(new UsernamePasswordAuthenticationToken(token, token));
  }
}
