package ru.tyomakr.akcp.auth.service;

import java.util.List;

public record JwtPrincipal(String username, List<String> roles) {
}
