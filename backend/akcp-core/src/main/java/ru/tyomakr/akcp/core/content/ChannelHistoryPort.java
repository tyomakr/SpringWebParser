package ru.tyomakr.akcp.core.content;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ChannelHistoryPort {
  CompletionStage<ChannelProfile> registerChannel(ChannelProfile channel);

  CompletionStage<ChannelHistoryTransition> recordPublicationEvidence(
      PublicationEvidence evidence
  );

  CompletionStage<Optional<ChannelHistoryTransition>> recordEligibilityDecision(
      ChannelEligibilityDecision decision
  );

  CompletionStage<HistoryMembershipState> findMembershipState(
      UUID mediaAssetId,
      UUID channelProfileId
  );
}
