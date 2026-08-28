package ru.tyomakr.akcp.ingestion.web.service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class UserAgentServiceImpl implements UserAgentService {
  private static final Logger log = LoggerFactory.getLogger(UserAgentServiceImpl.class);
  private static final String DEFAULT_UA = "Mozilla/5.0 (compatible; AKCP/1.0)";

  private final ResourceLoader resourceLoader;
  private final Random random = new Random();
  private final List<String> userAgents = new ArrayList<>();

  @Value("${akcp.ingestion.web.user-agents-file:classpath:user-agents.txt}")
  private String userAgentsPath;

  public UserAgentServiceImpl(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @PostConstruct
  void init() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        resourceLoader.getResource(userAgentsPath).getInputStream(),
        StandardCharsets.UTF_8))) {
      reader.lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .forEach(userAgents::add);
      log.info("Loaded {} user agents from {}", userAgents.size(), userAgentsPath);
    } catch (Exception ex) {
      log.warn("Failed to load user agents from {}: {}", userAgentsPath, ex.getMessage());
    }
  }

  @Override
  public String getRandomUserAgent() {
    if (userAgents.isEmpty()) {
      return DEFAULT_UA;
    }
    return userAgents.get(random.nextInt(userAgents.size()));
  }
}
