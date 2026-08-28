package ru.tyomakr.akcp.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.tyomakr.akcp.core.model.SavedSelection;
import ru.tyomakr.akcp.library.config.SelectionsProperties;
import ru.tyomakr.akcp.library.dto.SavedSelectionRequest;
import ru.tyomakr.akcp.library.repository.SavedSelectionRepository;

@ExtendWith(MockitoExtension.class)
class SavedSelectionServiceTest {
  private static final String USERNAME = "artem";
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ATTACHMENT_ID = UUID.randomUUID();

  @Mock
  private SavedSelectionRepository repository;

  private SelectionsProperties properties;
  private SavedSelectionService service;

  @BeforeEach
  void setUp() {
    properties = new SelectionsProperties();
    properties.setTtlHours(2);
    service = new SavedSelectionService(repository, properties);
  }

  @Test
  void savesSelectionWithUserScopeTargetAndConfiguredTtl() {
    when(repository.save(any(SavedSelection.class), anyString())).thenReturn(Mono.empty());

    SavedSelection selection = service.save(
        USERNAME,
        new SavedSelectionRequest(ITEM_ID, List.of(ATTACHMENT_ID), "web")
    ).block();

    assertThat(selection).isNotNull();
    assertThat(selection.username()).isEqualTo(USERNAME);
    assertThat(selection.itemId()).isEqualTo(ITEM_ID);
    assertThat(selection.attachmentIds()).containsExactly(ATTACHMENT_ID);
    assertThat(selection.target()).isEqualTo("web");
    assertThat(selection.expiresAt()).isBetween(
        selection.createdAt().plusSeconds(7199),
        selection.createdAt().plusSeconds(7201)
    );
    verify(repository).save(selection, ATTACHMENT_ID.toString());
  }

  @Test
  void rejectsInvalidInputBeforeRepositoryAccess() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.save(null, new SavedSelectionRequest(ITEM_ID, List.of(ATTACHMENT_ID), null)).block());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.save(USERNAME, new SavedSelectionRequest(null, List.of(ATTACHMENT_ID), null)).block());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.save(USERNAME, new SavedSelectionRequest(ITEM_ID, List.of(), null)).block());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.save(USERNAME, new SavedSelectionRequest(
            ITEM_ID,
            java.util.Collections.singletonList(null),
            null
        )).block());

    verifyNoInteractions(repository);
  }

  @Test
  void listsOnlyActiveRowsForRequestedUser() {
    Instant createdAt = Instant.parse("2026-08-28T10:00:00Z");
    Instant expiresAt = createdAt.plusSeconds(3600);
    when(repository.findByUser(eq(USERNAME), any(Instant.class))).thenReturn(Flux.just(
        new SavedSelectionRepository.SavedSelectionRow(
            UUID.randomUUID(),
            USERNAME,
            ITEM_ID,
            ATTACHMENT_ID.toString(),
            "web",
            createdAt,
            expiresAt
        )
    ));

    StepVerifier.create(service.list(USERNAME))
        .assertNext(selection -> {
          assertThat(selection.username()).isEqualTo(USERNAME);
          assertThat(selection.attachmentIds()).containsExactly(ATTACHMENT_ID);
          assertThat(selection.target()).isEqualTo("web");
          assertThat(selection.createdAt()).isEqualTo(createdAt);
          assertThat(selection.expiresAt()).isEqualTo(expiresAt);
        })
        .verifyComplete();
  }

  @Test
  void scopesDeleteAndCleanupToRepositoryOperations() {
    UUID selectionId = UUID.randomUUID();
    when(repository.deleteById(selectionId, USERNAME)).thenReturn(Mono.empty());
    when(repository.deleteExpired(any(Instant.class))).thenReturn(Mono.empty());

    StepVerifier.create(service.delete(USERNAME, selectionId)).verifyComplete();
    StepVerifier.create(service.cleanupExpired()).verifyComplete();

    verify(repository).deleteById(selectionId, USERNAME);
    verify(repository).deleteExpired(any(Instant.class));
  }
}
