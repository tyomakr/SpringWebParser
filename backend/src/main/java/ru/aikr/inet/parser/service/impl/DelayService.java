package ru.aikr.inet.parser.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
public class DelayService {
    @Value("${env.parser.min-parse-delay-ms}") private int minDelay;
    @Value("${env.parser.max-parse-delay-ms}") private int maxDelay;
    private final Random random = new Random();

    public void humanDelay() {
        try {
            Thread.sleep(minDelay + random.nextInt(maxDelay - minDelay));
        } catch (InterruptedException ignored) {}
    }
}