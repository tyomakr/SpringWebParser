package ru.tyomakr.akcp.core.content;

import java.util.concurrent.CompletionStage;

/** Publisher boundary. Implementations must not be called without an explicit proposal. */
public interface PublisherPort {
  PublicationPlatform platform();

  CompletionStage<PublicationAttemptResult> publish(PublishProposal proposal);

  CompletionStage<PublicationAttemptResult> reconcile(PublishProposal proposal);
}
