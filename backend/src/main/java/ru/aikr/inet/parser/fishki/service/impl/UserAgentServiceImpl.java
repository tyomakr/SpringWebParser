package ru.aikr.inet.parser.fishki.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.fishki.service.UserAgentService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Сервис, который возвращает случайный User‑Agent из файла.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAgentServiceImpl implements UserAgentService {

    private final Random random = new Random();

    /** Путь берётся из application.yml (env.global.user-agents-file). */
    @Value("${env.global.user-agents-file:classpath:user-agents.txt}")
    private String userAgentsPath;

    /** Загруженные строки. */
    private final List<String> userAgents = new ArrayList<>();

    @PostConstruct
    void init() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                getClass().getClassLoader().getResourceAsStream(
                                        Objects.requireNonNull(
                                                userAgentsPath,
                                                "env.global.user-agents-file must not be null")),
                                "User-Agents resource not found: " + userAgentsPath),
                        StandardCharsets.UTF_8))) {

            br.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(userAgents::add);

            log.info("Loaded {} User-Agents from {}", userAgents.size(), userAgentsPath);
        } catch (Exception e) {
            log.error("User-Agent load error: {}", e.getMessage());
        }
    }

    @Override
    public String getRandomUserAgent() {
        return userAgents.isEmpty()
                ? "Mozilla/5.0 (compatible; SpringWebParser/1.0)"
                : userAgents.get(random.nextInt(userAgents.size()));
    }
}
