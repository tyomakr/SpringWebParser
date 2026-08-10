package ru.tyomakr.akcp.core.content;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublicationEvidence(
    PublicationEvidenceStatus status,
    PublicationEvidenceSource source,
    UUID mediaAssetId,
    UUID channelProfileId,
    PublicationOccurrence occurrence,
    Instant observedAt
) {
  public PublicationEvidence {
    Objects.requireNonNull(status, "status is required");
    Objects.requireNonNull(source, "source is required");
    Objects.requireNonNull(mediaAssetId, "mediaAssetId is required");
    Objects.requireNonNull(channelProfileId, "channelProfileId is required");
    Objects.requireNonNull(observedAt, "observedAt is required");
    if (status == PublicationEvidenceStatus.CONFIRMED && occurrence == null) {
      throw new IllegalArgumentException("confirmed evidence requires publication occurrence");
    }
    if (status != PublicationEvidenceStatus.CONFIRMED && occurrence != null) {
      throw new IllegalArgumentException("unconfirmed evidence must not contain publication occurrence");
    }
    if (occurrence != null
        && (!mediaAssetId.equals(occurrence.mediaAssetId())
            || !channelProfileId.equals(occurrence.channelProfileId()))) {
      throw new IllegalArgumentException("publication occurrence must match evidence scope");
    }
    if (status != PublicationEvidenceStatus.CONFIRMED
        && source != PublicationEvidenceSource.PUBLISH_ATTEMPT) {
      throw new IllegalArgumentException("reconciliation and external import must be confirmed");
    }
  }
}
