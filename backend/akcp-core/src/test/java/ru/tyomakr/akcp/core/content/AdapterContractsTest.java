package ru.tyomakr.akcp.core.content;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdapterContractsTest {
  private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

  @Test
  void sourceBatchDefensivelyCopiesBytesAndNormalizesOptionalScope() {
    byte[] bytes = {1, 2, 3};
    SourceImportRecord record = new SourceImportRecord(
        " post-1 ",
        " post-1 ",
        " media-1 ",
        null,
        " ",
        "image/jpeg",
        10,
        20,
        bytes,
        "fixture",
        NOW
    );
    bytes[0] = 9;

    assertEquals("post-1", record.sourceRecordId());
    assertEquals("post-1", record.externalPostId());
    assertEquals(null, record.mediaUrl());
    assertArrayEquals(new byte[] {1, 2, 3}, record.content());
    byte[] returned = record.content();
    returned[1] = 8;
    assertArrayEquals(new byte[] {1, 2, 3}, record.content());
  }

  @Test
  void sourceBatchRejectsDuplicateSourceRecords() {
    SourceImportRecord record = record("same");
    assertThrows(IllegalArgumentException.class, () -> new SourceImportBatch(
        UUID.randomUUID(),
        SourcePlatform.VK,
        "group-1",
        List.of(record, record)
    ));
    assertThrows(IllegalArgumentException.class, () -> new SourceImportResult(
        UUID.randomUUID(),
        1,
        1,
        0,
        java.util.Collections.singletonList(null)
    ));
  }

  @Test
  void publisherProposalRequiresAuthorizationAndIdempotency() {
    UUID assetId = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> proposal(assetId, " ", "key-1"));
    assertThrows(IllegalArgumentException.class, () -> proposal(assetId, "operator-1", " "));

    PublishProposal proposal = proposal(assetId, "operator-1", "key-1");
    assertEquals("operator-1", proposal.approval().operatorReference());
    assertEquals("key-1", proposal.idempotencyKey());
  }

  @Test
  void publisherPortKeepsAttemptOutcomeExplicit() throws Exception {
    PublisherPort fake = new PublisherPort() {
      @Override
      public PublicationPlatform platform() {
        return PublicationPlatform.VK;
      }

      @Override
      public java.util.concurrent.CompletionStage<PublicationAttemptResult> publish(
          PublishProposal proposal
      ) {
        return java.util.concurrent.CompletableFuture.completedFuture(new PublicationAttemptResult(
            proposal.proposalId(),
            platform(),
            PublicationAttemptStatus.UNKNOWN,
            List.of(),
            NOW,
            "fixture outcome"
        ));
      }

      @Override
      public java.util.concurrent.CompletionStage<PublicationAttemptResult> reconcile(
          PublishProposal proposal
      ) {
        return publish(proposal);
      }
    };

    PublicationAttemptResult result = fake.publish(proposal(
        UUID.randomUUID(),
        "operator-1",
        "key-2"
    )).toCompletableFuture().get();
    assertEquals(PublicationAttemptStatus.UNKNOWN, result.status());
    assertEquals("fixture outcome", result.detail());
    assertEquals(PublicationAttemptStatus.UNKNOWN, fake.reconcile(
        proposal(UUID.randomUUID(), "operator-1", "key-reconcile")
    ).toCompletableFuture().get().status());

    assertThrows(IllegalArgumentException.class, () -> new PublishProposal(
        UUID.randomUUID(),
        PublicationPlatform.TELEGRAM,
        "telegram-channel-1",
        List.of(UUID.randomUUID()),
        new PublicationApproval(UUID.randomUUID(), PublicationPlatform.VK, "operator-1", NOW),
        "key-3",
        NOW
    ));
  }

  private SourceImportRecord record(String id) {
    return new SourceImportRecord(
        id,
        null,
        null,
        null,
        null,
        "image/jpeg",
        10,
        10,
        new byte[] {1},
        null,
        NOW
    );
  }

  private PublishProposal proposal(UUID assetId, String authorization, String idempotencyKey) {
    return new PublishProposal(
        UUID.randomUUID(),
        PublicationPlatform.VK,
        "vk-group-1",
        List.of(assetId),
        new PublicationApproval(UUID.randomUUID(), PublicationPlatform.VK, authorization, NOW),
        idempotencyKey,
        NOW
    );
  }
}
