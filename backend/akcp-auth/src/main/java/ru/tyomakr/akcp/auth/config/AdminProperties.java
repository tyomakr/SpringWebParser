package ru.tyomakr.akcp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "akcp.admin")
public record AdminProperties(
    String username,
    String password
) {
}
