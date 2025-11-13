package ru.aikr.inet.parser.mlpublish.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.server.reactive.ReactiveHttpOutputMessage;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class HttpMlRecommendationClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void shouldReturnRecommendationsOnSuccess() {
        ExchangeFunction exchangeFunction = Objects.requireNonNull(mock(ExchangeFunction.class));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        ClientResponse response = recommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.9, "good",
                        "publish", "hit", "hash1"),
                new MlRecommendationResponse.MlRecommendationItem("2", "https://example.com/2.jpg", 0.3, "bad",
                        "skip", "miss", "hash2")));
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
        ClientResponse response = stringResponse(HttpStatus.INTERNAL_SERVER_ERROR, "oops");
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
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
        ClientResponse response = recommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("1", "https://example.com/1.jpg", 0.6, "wtf",
                        "wtf", "gray", "hash-wtf")));
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();

        assertNotNull(actual);
        assertEquals(RecommendationDecision.SKIP, actual.get(0).decision());
    }

    @Test
    void shouldFailOnEmptyPayload() {
        ClientResponse response = recommendationResponse(null);
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        MlRecommendationException thrown = assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());
        assertEquals("ML service returned empty payload", thrown.getMessage());
    }

    @Test
    void shouldReturnEmptyForEmptyCandidates() {
        ExchangeFunction exchangeFunction = Objects.requireNonNull(mock(ExchangeFunction.class));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(Collections.emptyList()).block();

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
        verifyNoInteractions(exchangeFunction);
    }

    @Test
    void shouldCallWithoutApiKeyAndReturnMappedItems() {
        ClientResponse response = recommendationResponse(List.of(
                new MlRecommendationResponse.MlRecommendationItem("x", "url", 0.1, "reason", "skip", "miss", "hash")));
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        properties.setApiKey(null);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

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
                .mapToObj(call -> recommendationResponse(IntStream.range(call * 100, Math.min(call * 100 + 100, many.size()))
                        .mapToObj(i -> new MlRecommendationResponse.MlRecommendationItem(
                                String.valueOf(i), "https://example.com/" + i, 0.5, "reason",
                                "publish", "hit", "hash-" + i))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) ->
                Mono.just(responses.get(count.getAndIncrement())));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MlRecommendationProperties properties = withDefaults();
        properties.setMaxBatchSize(100);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(many).block();
        assertNotNull(actual);
        assertEquals(371, actual.size());
        assertEquals("0", actual.get(0).id());
        assertEquals("370", actual.get(actual.size() - 1).id());
        assertEquals(4, count.get());
    }

    @Test
    void unauthorizedWhenRequireApiKeyFalseThrows() {
        ClientResponse response = stringResponse(HttpStatus.UNAUTHORIZED, "nope");
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        MlRecommendationProperties properties = withDefaults();
        properties.setRequireApiKey(false);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        assertThrows(MlRecommendationException.class,
                () -> client.recommend(sampleImages()).block());
    }

    @Test
    void unauthorizedWhenRequireApiKeyTrueFallsBack() {
        ClientResponse response = stringResponse(HttpStatus.UNAUTHORIZED, "nope");
        ExchangeFunction exchangeFunction = Objects.requireNonNull((ClientRequest request) -> Mono.just(response));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        MlRecommendationProperties properties = withDefaults();
        properties.setRequireApiKey(true);
        HttpMlRecommendationClient client = new HttpMlRecommendationClient(webClient, properties);

        List<MlRecommendation> actual = client.recommend(sampleImages()).block();
        assertNotNull(actual);
        assertTrue(actual.isEmpty());
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

    private static ClientResponse recommendationResponse(List<MlRecommendationResponse.MlRecommendationItem> items) {
        return createResponse(HttpStatus.OK, new MlRecommendationResponse(items));
    }

    private static ClientResponse stringResponse(HttpStatus status, String body) {
        return createResponse(status, body);
    }

    private static ClientResponse createResponse(HttpStatus status, Object payload) {
        return ClientResponse.create(status)
                .body((ReactiveHttpOutputMessage outputMessage) -> {
                    byte[] bytes = toBytes(payload);
                    DataBuffer buffer = BUFFER_FACTORY.wrap(bytes);
                    return outputMessage.writeWith(Mono.just(buffer));
                })
                .build();
    }

    private static byte[] toBytes(Object payload) {
        try {
            if (payload instanceof String string) {
                return string.getBytes(StandardCharsets.UTF_8);
            }
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
