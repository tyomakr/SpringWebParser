package ru.aikr.inet.parser.mlpublish.exception;

/**
 * Exception propagated when ML recommendation call fails.
 */
public class MlRecommendationException extends RuntimeException {

    public MlRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MlRecommendationException(String message) {
        super(message);
    }
}
