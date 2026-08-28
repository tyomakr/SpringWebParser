package ru.tyomakr.akcp.auth.service;

import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.auth.config.AdminProperties;
import ru.tyomakr.akcp.auth.dto.LoginRequest;
import ru.tyomakr.akcp.auth.dto.LoginResponse;
import ru.tyomakr.akcp.core.model.UserRole;

@Service
public class AuthService {
  private final AdminProperties adminProperties;
  private final JwtService jwtService;

  public AuthService(AdminProperties adminProperties, JwtService jwtService) {
    this.adminProperties = adminProperties;
    this.jwtService = jwtService;
  }

  public Mono<LoginResponse> login(LoginRequest request) {
    if (adminProperties.username().equals(request.username())
        && adminProperties.password().equals(request.password())) {
      String token = jwtService.issueToken(request.username(), List.of(UserRole.ADMIN.name()));
      return Mono.just(LoginResponse.fromToken(token));
    }
    return Mono.error(new IllegalArgumentException("Invalid credentials"));
  }
}
