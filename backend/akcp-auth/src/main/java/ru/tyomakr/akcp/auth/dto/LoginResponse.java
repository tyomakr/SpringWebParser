package ru.tyomakr.akcp.auth.dto;

public record LoginResponse(String token, String tokenType) {
  public static LoginResponse fromToken(String token) {
    return new LoginResponse(token, "Bearer");
  }
}
