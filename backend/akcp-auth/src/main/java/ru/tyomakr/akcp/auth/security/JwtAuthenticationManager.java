package ru.tyomakr.akcp.auth.security;

import java.util.List;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.auth.service.JwtPrincipal;
import ru.tyomakr.akcp.auth.service.JwtService;

public class JwtAuthenticationManager implements ReactiveAuthenticationManager {
  private final JwtService jwtService;

  public JwtAuthenticationManager(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public Mono<Authentication> authenticate(Authentication authentication) {
    String token = (String) authentication.getCredentials();
    return Mono.fromCallable(() -> jwtService.verify(token))
        .map(this::toAuthentication);
  }

  private Authentication toAuthentication(JwtPrincipal principal) {
    List<SimpleGrantedAuthority> authorities = principal.roles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
    return new UsernamePasswordAuthenticationToken(principal.username(), null, authorities);
  }
}
