package ru.tyomakr.akcp.auth.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.auth.config.AdminProperties;
import ru.tyomakr.akcp.auth.service.JwtService;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, JwtService jwtService) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(exchange -> exchange
            .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .pathMatchers("/api/auth/login").permitAll()
            .pathMatchers("/actuator/health").permitAll()
            .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
            .pathMatchers("/api/publish/vk/**").hasRole("ADMIN")
            .pathMatchers("/api/ingestion/web/**").hasAnyRole("ADMIN", "MODERATOR")
            .pathMatchers("/api/items/**").hasAnyRole("ADMIN", "MODERATOR", "AGENT")
            .pathMatchers("/api/**").authenticated()
            .anyExchange().permitAll())
        .build();
  }

  @Bean
  public ReactiveUserDetailsService reactiveUserDetailsService(AdminProperties adminProperties) {
    return new MapReactiveUserDetailsService(
        User.withUsername(adminProperties.username())
            .password("{noop}" + adminProperties.password())
            .roles("ADMIN")
            .build());
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public WebFilter jwtWebFilter(JwtService jwtService) {
    return (exchange, chain) -> {
      String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
      if (header == null || !header.startsWith("Bearer ")) {
        return chain.filter(exchange);
      }
      String token = header.substring(7);
      return Mono.fromCallable(() -> jwtService.verify(token))
          .map(principal -> {
            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
            return (Authentication) new UsernamePasswordAuthenticationToken(
                principal.username(), token, authorities);
          })
          .flatMap(authentication -> chain.filter(exchange)
              .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
          .onErrorResume(ex -> chain.filter(exchange));
    };
  }
}
