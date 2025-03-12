package ru.aikr.inet.parser.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import ru.aikr.inet.parser.service.UserAgentService;
import ru.aikr.inet.parser.util.AnsiColors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class UserAgentServiceImpl implements UserAgentService {
    private static final Logger log = Logger.getLogger(UserAgentServiceImpl.class.getName());
    private final Random random = new Random();
    private List<String> userAgents;

    @Value("${env.global.user-agents-file}")
    private String userAgentsFile;

    @PostConstruct
    public void init() {
        loadUserAgents();
        log.info(AnsiColors.CYAN + "Loaded " + userAgents.size() + " User-Agents" + AnsiColors.RESET);
    }

    private void loadUserAgents() {
        try {
            File file = ResourceUtils.getFile(userAgentsFile);
            userAgents = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            log.severe(AnsiColors.RED + "User-Agent load error: " + e.getMessage() + AnsiColors.RESET);
            userAgents = List.of(); // Fallback
        }
    }

    @Override
    public String getRandomUserAgent() {
        if (userAgents.isEmpty()) {
            log.warning(AnsiColors.YELLOW + "No User-Agents available. Using default." + AnsiColors.RESET);
            return "Mozilla/5.0 (compatible; SpringWebParser/1.0)";
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }
}