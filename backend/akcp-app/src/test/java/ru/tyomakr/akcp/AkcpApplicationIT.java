package ru.tyomakr.akcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.tyomakr.akcp.auth.dto.LoginRequest;
import ru.tyomakr.akcp.auth.dto.LoginResponse;
import ru.tyomakr.akcp.auth.service.JwtService;
import ru.tyomakr.akcp.core.model.UserRole;
import ru.tyomakr.akcp.library.dto.AttachmentRequest;
import ru.tyomakr.akcp.library.dto.CreateItemRequest;
import ru.tyomakr.akcp.library.dto.ItemListResponse;
import ru.tyomakr.akcp.library.dto.ItemResponse;
import ru.tyomakr.akcp.publishing.vk.dto.PublishJobResponse;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AkcpApplicationIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("akcp")
      .withUsername("akcp")
      .withPassword("akcp");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.r2dbc.url", () ->
        String.format("r2dbc:postgresql://%s:%d/%s", POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
    registry.add("spring.r2dbc.username", POSTGRES::getUsername);
    registry.add("spring.r2dbc.password", POSTGRES::getPassword);
    registry.add("spring.flyway.url", () ->
        String.format("jdbc:postgresql://%s:%d/%s", POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("akcp.jwt.secret", () -> "test-secret");
    registry.add("akcp.admin.username", () -> "admin");
    registry.add("akcp.admin.password", () -> "admin");
  }

  @LocalServerPort
  private int port;

  private WebTestClient webTestClient;

  @Autowired
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    this.webTestClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
  }

  @Test
  void loginCreateAndListItems() {
    LoginResponse login = loginAsAdmin();

    assertThat(login).isNotNull();

    CreateItemRequest createRequest = new CreateItemRequest(
        "Test title",
        "Test content",
        null,
        null,
        List.of(new AttachmentRequest("IMAGE", "https://example.com/a.jpg", null)),
        List.of("test")
    );

    ItemResponse created = webTestClient.post()
        .uri("/api/items")
        .header("Authorization", "Bearer " + login.token())
        .bodyValue(createRequest)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ItemResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(created).isNotNull();

    ItemListResponse listResponse = webTestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/api/items").queryParam("limit", 1).build())
        .header("Authorization", "Bearer " + login.token())
        .exchange()
        .expectStatus().isOk()
        .expectBody(ItemListResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(listResponse).isNotNull();
    assertThat(listResponse.items()).hasSize(1);
  }

  @Test
  void anonymousRequestsAreRejected() {
    webTestClient.get()
        .uri("/api/items")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void publishRequiresAdminRole() {
    LoginResponse login = loginAsAdmin();

    CreateItemRequest createRequest = new CreateItemRequest(
        "Publish title",
        "Publish content",
        null,
        null,
        List.of(new AttachmentRequest("IMAGE", "https://example.com/p.jpg", null)),
        List.of("publish")
    );

    ItemResponse created = webTestClient.post()
        .uri("/api/items")
        .header("Authorization", "Bearer " + login.token())
        .bodyValue(createRequest)
        .exchange()
        .expectStatus().isOk()
        .expectBody(ItemResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(created).isNotNull();

    String moderatorToken = jwtService.issueToken("moderator", List.of(UserRole.MODERATOR.name()));

    webTestClient.post()
        .uri("/api/publish/vk/" + created.id())
        .header("Authorization", "Bearer " + moderatorToken)
        .exchange()
        .expectStatus().isForbidden();

    PublishJobResponse response = webTestClient.post()
        .uri("/api/publish/vk/" + created.id())
        .header("Authorization", "Bearer " + login.token())
        .exchange()
        .expectStatus().isOk()
        .expectBody(PublishJobResponse.class)
        .returnResult()
        .getResponseBody();

    assertThat(response).isNotNull();
  }

  private LoginResponse loginAsAdmin() {
    return webTestClient.post()
        .uri("/api/auth/login")
        .bodyValue(new LoginRequest("admin", "admin"))
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoginResponse.class)
        .returnResult()
        .getResponseBody();
  }
}
