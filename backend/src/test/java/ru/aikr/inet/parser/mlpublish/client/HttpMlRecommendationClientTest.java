package ru.aikr.inet.parser.mlpublish.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationException;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationUnauthorizedException;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendation;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendationRequest;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendationResponse;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class HttpMlRecommendationClientTest {

    @Test
    void shouldReturnRecommendationsOnSuccess() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        ClientResponse response = mock(ClientResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.OK);

        MlRecommendationResponse payload = new MlRecommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.9, "good",
                        "publish"),
                new MlRecommendationResponse.MlRecommendationItem("2", "https://example.com/2.jpg", 0.3, "bad",
                        "skip")));
        when(response.bodyToMono(MlRecommendationResponse.class)).thenReturn(Mono.just(payload));

        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(response));

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals("1", actual.get(0).id());
        assertEquals("https://example.com/1.jpg", actual.get(0).url());
        assertEquals(0.9, actual.get(0).score());
        assertEquals("good", actual.get(0).reason());
        assertEquals(RecommendationDecision.PUBLISH, actual.get(0).decision());
        assertEquals(RecommendationDecision.SKIP, actual.get(1).decision());
    }

    @Test
    void shouldPropagateStatusError() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse
                        .create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("oops")
                        .build());

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        MlRecommendationException thrown = assertThrows(
                MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());

        assertTrue(thrown.getMessage().contains("500"));
        assertTrue(thrown.getMessage().contains("oops"));
    }

    @Test
    void shouldFallbackOnUnknownDecision() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        ClientResponse response = mock(ClientResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.OK);
        MlRecommendationResponse payload = new MlRecommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.6, "wtf",
                        "wtf")));
        when(response.bodyToMono(MlRecommendationResponse.class)).thenReturn(Mono.just(payload));
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(response));

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertEquals(RecommendationDecision.SKIP, actual.get(0).decision());
    }

    @Test
    void shouldFailOnEmptyPayload() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        ClientResponse response = mock(ClientResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.OK);
        MlRecommendationResponse payload = new MlRecommendationResponse(null);
        when(response.bodyToMono(MlRecommendationResponse.class)).thenReturn(Mono.just(payload));
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(response));

        MlRecommendationException thrown = assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());
        assertEquals("ML service returned empty payload", thrown.getMessage());
    }

    @Test
    void shouldReturnEmptyForEmptyCandidates() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(Collections.emptyList()).block();

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
        verifyNoInteractions(exchangeFunction);
    }

    private static List<WebImage> sampleImages() {
        return List.of(
                new WebImage("1", "https://example.com/1.jpg"),
                new WebImage("2", "https://example.com/2.jpg"));
    }

    private static MlRecommendationProperties withDefaults() {
        MlRecommendationProperties properties = new MlRecommendationProperties();
        properties.setRecommendationPath("/recommend");
        properties.setTimeoutSeconds(1);
        properties.setApiKey("dummy");
        return properties;
    }

    @Test
    void shouldFallbackOnUnauthorized() {
        ExchangeFunction exchangeFunction = request -> {
            assertEquals("Bearer dummy", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return Mono.just(
                    ClientResponse
                        .create(HttpStatus.UNAUTHORIZED)
                        .body("nope")
                        .build());
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    void shouldFallbackOnTimeout() {
        ExchangeFunction exchangeFunction = request -> Mono.<ClientResponse>never();

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        MlRecommendationProperties properties = withDefaults();
        properties.setTimeoutSeconds(0);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }
}
