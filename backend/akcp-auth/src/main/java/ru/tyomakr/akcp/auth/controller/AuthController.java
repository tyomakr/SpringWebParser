package ru.tyomakr.akcp.auth.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.tyomakr.akcp.auth.dto.LoginRequest;
import ru.tyomakr.akcp.auth.dto.LoginResponse;
import ru.tyomakr.akcp.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }
}
