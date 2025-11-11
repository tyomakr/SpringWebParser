package ru.aikr.inet.parser.recommendation;

/**
 * Исключение, которое бросает RecommendationClient при ошибке HTTP или таймауте.
 */
public class RecommendationException extends RuntimeException {

    public RecommendationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecommendationException(String message) {
        super(message);
    }
}
