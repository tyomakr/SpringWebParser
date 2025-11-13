package ru.aikr.inet.parser.mlpublish.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.recommendation.model.RecommendationDecision;
import ru.aikr.inet.parser.mlpublish.config.MlRecommendationProperties;
import ru.aikr.inet.parser.mlpublish.exception.MlRecommendationException;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendation;
import ru.aikr.inet.parser.mlpublish.model.MlRecommendationResponse;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HttpMlRecommendationClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void shouldReturnRecommendationsOnSuccess() {
        ExchangeFunction exchangeFunction = request -> Mono.just(jsonResponse(HttpStatus.OK, new MlRecommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.9, "good", "publish", "hit", "hash1"),
                new MlRecommendationResponse.MlRecommendationItem("2", "https://example.com/2.jpg", 0.3, "bad", "skip", "miss", "hash2")
        ))));

        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals("1", actual.get(0).id());
        assertEquals(RecommendationDecision.PUBLISH, actual.get(0).decision());
        assertEquals(RecommendationDecision.SKIP, actual.get(1).decision());
    }

    @Test
    void shouldPropagateStatusError() {
        ExchangeFunction exchangeFunction = request -> Mono.just(stringResponse(HttpStatus.INTERNAL_SERVER_ERROR, "oops"));
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        MlRecommendationException thrown = assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());

        assertTrue(thrown.getMessage().contains("500"));
        assertTrue(thrown.getMessage().contains("oops"));
    }

    @Test
    void shouldFallbackOnUnknownDecision() {
        ExchangeFunction exchangeFunction = request -> Mono.just(jsonResponse(HttpStatus.OK, new MlRecommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.6, "wtf", "wtf", "gray", "hash-wtf")
        ))));
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertEquals(RecommendationDecision.SKIP, actual.get(0).decision());
    }

    @Test
    void shouldFailOnEmptyPayload() {
        ExchangeFunction exchangeFunction = request -> Mono.just(jsonResponse(HttpStatus.OK, new MlRecommendationResponse(null)));
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        MlRecommendationException thrown = assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());
        assertEquals("ML service returned empty payload", thrown.getMessage());
    }

    @Test
    void shouldReturnEmptyForEmptyCandidates() {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        List<MlRecommendation> actual = client.recommend(Collections.emptyList()).block();

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void shouldCallWithoutApiKeyAndReturnMappedItems() {
        ExchangeFunction exchangeFunction = request -> Mono.just(jsonResponse(HttpStatus.OK, new MlRecommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("x", "url", 0.1, "reason", "skip", "miss", "hash")
        ))));
        MlRecommendationProperties properties = withDefaults();
        properties.setApiKey(null);

        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();
        assertNotNull(actual);
        assertEquals(1, actual.size());
    }

    @Test
    void shouldChunkLargeCandidateListAndPreserveOrder() {
        AtomicInteger count = new AtomicInteger();
        List<WebImage> many = IntStream.range(0, 371)
                .mapToObj(i -> new WebImage(String.valueOf(i), "https://example.com/" + i))
                .collect(Collectors.toList());
        List<ClientResponse> responses = IntStream.range(0, 4)
                .mapToObj(call -> jsonResponse(HttpStatus.OK, new MlRecommendationResponse(IntStream.range(call * 100, Math.min(call * 100 + 100, many.size()))
                        .mapToObj(i -> new MlRecommendationResponse.MlRecommendationItem(
                                String.valueOf(i), "https://example.com/" + i, 0.5, "reason", "publish", "hit", "hash-" + i))
                        .collect(Collectors.toList()))))
                .collect(Collectors.toList());
        ExchangeFunction exchangeFunction = request -> Mono.just(responses.get(count.getAndIncrement()));

        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), withDefaults());

        List<MlRecommendation> actual = client.recommend(many).block();
        assertNotNull(actual);
        assertEquals(371, actual.size());
        assertEquals("0", actual.get(0).id());
        assertEquals("370", actual.get(actual.size() - 1).id());
    }

    @Test
    void unauthorizedWhenRequireApiKeyFalseThrows() {
        ExchangeFunction exchangeFunction = request -> Mono.just(stringResponse(HttpStatus.UNAUTHORIZED, "nope"));
        MlRecommendationProperties properties = withDefaults();
        properties.setRequireApiKey(false);

        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), properties);

        assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());
    }

    @Test
    void unauthorizedWhenRequireApiKeyTrueFallsBack() {
        ExchangeFunction exchangeFunction = request -> Mono.just(stringResponse(HttpStatus.UNAUTHORIZED, "nope"));
        MlRecommendationProperties properties = withDefaults();
        properties.setRequireApiKey(true);

        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient(exchangeFunction), properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();
        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    private static WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .exchangeFunction(Objects.requireNonNull(exchangeFunction, "exchangeFunction must not be null"))
                .build();
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
        properties.setMaxBatchSize(100);
        properties.setRequireApiKey(false);
        return properties;
    }

    private static ClientResponse jsonResponse(HttpStatus status, Object payload) {
        return response(status, payload, MediaType.APPLICATION_JSON);
    }

    private static ClientResponse stringResponse(HttpStatus status, String payload) {
        return response(status, payload, MediaType.TEXT_PLAIN);
    }

    private static ClientResponse response(HttpStatus status, Object payload, MediaType mediaType) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        byte[] bytes = payload instanceof String str
                ? str.getBytes(StandardCharsets.UTF_8)
                : writeBytes(payload);
        Objects.requireNonNull(bytes, "payload bytes must not be null");
        DataBuffer buffer = BUFFER_FACTORY.wrap(bytes);
        Flux<DataBuffer> body = Flux.just(buffer);
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(Objects.requireNonNull(body, "body must not be null"))
                .build();
    }

    private static byte[] writeBytes(Object payload) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
