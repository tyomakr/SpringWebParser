package ru.tyomakr.akcp.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "akcp.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    Duration ttl
) {
}
