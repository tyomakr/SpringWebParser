package ru.aikr.inet.parser.mlpublish.client;

import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendation;

import java.util.List;

/**
 * Client abstraction for the ML publishing recommendation endpoint.
 */
public interface MlRecommendationClient {
    Mono<List<MlRecommendation>> recommend(List<WebImage> candidates);
}
