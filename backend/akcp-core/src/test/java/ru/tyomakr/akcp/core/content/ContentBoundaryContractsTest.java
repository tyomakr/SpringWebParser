package ru.tyomakr.akcp.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContentBoundaryContractsTest {
  private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

  @Test
  void sameMediaCanHaveMultipleIndependentSourceOccurrences() {
    UUID mediaAssetId = UUID.randomUUID();
    SourceOccurrence vkOccurrence = occurrence(mediaAssetId, SourcePlatform.VK, "vk:photo:1");
    SourceOccurrence webOccurrence = occurrence(mediaAssetId, SourcePlatform.WEB, "web:image:1");

    assertEquals(vkOccurrence.mediaAssetId(), webOccurrence.mediaAssetId());
    assertNotEquals(vkOccurrence.id(), webOccurrence.id());
    assertNotEquals(vkOccurrence.platform(), webOccurrence.platform());
  }

  @Test
  void publicationReferencesMediaAndChannelWithoutChangingMediaIdentity() {
    UUID mediaAssetId = UUID.randomUUID();
    ChannelProfile channel = new ChannelProfile(
        UUID.randomUUID(),
        PublicationPlatform.TELEGRAM,
        "-100123456",
        "Moderated feed"
    );
    PublicationOccurrence publication = new PublicationOccurrence(
        UUID.randomUUID(),
        mediaAssetId,
        channel.id(),
        "telegram:message:42",
        NOW
    );

    assertEquals(mediaAssetId, publication.mediaAssetId());
    assertEquals(channel.id(), publication.channelProfileId());
    assertEquals(PublicationPlatform.TELEGRAM, channel.platform());
  }

  @Test
  void mediaIdentityNormalizesSha256WithoutDependingOnSource() {
    String upperSha = "ABCDEF0123456789".repeat(4);
    MediaAsset asset = new MediaAsset(
        UUID.randomUUID(),
        upperSha,
        "image/jpeg",
        1280,
        720,
        "sha256/ab/cd/image"
    );

    assertEquals(upperSha.toLowerCase(), asset.sha256());
  }

  private static SourceOccurrence occurrence(
      UUID mediaAssetId,
      SourcePlatform platform,
      String externalMediaId
  ) {
    return new SourceOccurrence(
        UUID.randomUUID(),
        mediaAssetId,
        platform,
        externalMediaId,
        null,
        null,
        null,
        null,
        "https://example.test/image.jpg",
        null,
        NOW
    );
  }
}
