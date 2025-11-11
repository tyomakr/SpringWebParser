package ru.aikr.inet.parser.mlpublish;

/**
 * Marker exception used to identify unauthorized responses from the ML recommendation service.
 */
public class MlRecommendationUnauthorizedException extends MlRecommendationException {

    public MlRecommendationUnauthorizedException(String message) {
        super(message);
    }
}
