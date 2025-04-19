package ru.aikr.inet.parser.exception;

import org.springframework.stereotype.Component;
import ru.aikr.inet.parser.util.AnsiColors;

import java.util.logging.Logger;

@Component
public class ErrorProcessor {

    private static final Logger log = Logger.getLogger(ErrorProcessor.class.getName());

    public void handlePageError(int pageNumber, String url, Exception e) {
        log.severe(AnsiColors.RED + String.format(
                "Page %d error: %s\nURL: %s",
                pageNumber, e.getMessage(), url
        ) + AnsiColors.RESET);
    }
}