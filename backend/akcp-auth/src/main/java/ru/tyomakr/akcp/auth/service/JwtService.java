package ru.tyomakr.akcp.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.tyomakr.akcp.auth.config.JwtProperties;

@Service
public class JwtService {
  private final JwtProperties properties;
  private final Algorithm algorithm;
  private final JWTVerifier verifier;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    this.algorithm = Algorithm.HMAC256(properties.secret());
    this.verifier = JWT.require(algorithm)
        .withIssuer(properties.issuer())
        .build();
  }

  public String issueToken(String username, List<String> roles) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.ttl());
    return JWT.create()
        .withIssuer(properties.issuer())
        .withSubject(username)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(expiresAt))
        .withClaim("roles", roles)
        .sign(algorithm);
  }

  public JwtPrincipal verify(String token) {
    try {
      DecodedJWT jwt = verifier.verify(token);
      List<String> roles = jwt.getClaim("roles").asList(String.class);
      return new JwtPrincipal(jwt.getSubject(), roles == null ? List.of() : roles);
    } catch (JWTVerificationException ex) {
      throw new IllegalArgumentException("Invalid token", ex);
    }
  }
}
