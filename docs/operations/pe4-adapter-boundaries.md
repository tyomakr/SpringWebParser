# PE4 adapter boundary and rollout notes

Status: additive contract slice; runtime wiring is intentionally disabled.

## Boundary

`AuthorizedExportSourceAdapter` only validates the source platform and emits a
normalized `SourceImportBatch`. `SourceIngestionCoordinator` owns storage,
catalog matching and source-occurrence idempotency. This follows ADR-0003 and
keeps source adapters independent from publisher credentials.

The current fixture path is bounded and caller-supplied. It does not fetch
URLs, call VK/Telegram/Instagram APIs, read cookies or credentials, or publish.
Instagram remains a future adapter decision.

## Replay and failure semantics

The PE4 slice replays a batch from its beginning. Repeated records are
idempotent because media identity and source-occurrence keys are deterministic
and the catalog rejects changed content for an existing source record. A
cursor/per-record progress API is deliberately deferred until a real authorized
export format and rate-limit contract are selected.

Storage is written before catalog registration. If catalog registration fails,
content-addressed bytes can remain as an orphan object; no deletion or garbage
collection is attempted by this slice. Before runtime wiring or large imports,
add a reconciliation/GC operation and a failure-injection test proving that
orphan handling is observable and bounded.

## Publisher boundary

`PublisherPort` accepts only a `PublishProposal` carrying an explicit
`PublicationApproval` and idempotency key. It exposes `reconcile` for
`UNKNOWN` outcomes. No current VK implementation is adapted or wired by this
slice because the worktree contains an independent, uncommitted publishing
implementation. A future adapter must persist idempotency and attempt history
before live publication is enabled.

## Safe rollout

1. Keep the contracts and fixture adapter unused by runtime.
2. Add fake-server contract tests for a selected official/export format.
3. Add a persistent progress/reconciliation design and review rights,
   credentials and rate limits.
4. Wire one authorized source adapter behind an explicit feature switch.
5. Keep recommendation and publication permissions separate; no automatic
   publication is authorized by this document.

Rollback is a code revert while runtime wiring is disabled. Once a persistent
adapter is enabled, use forward-only corrective migrations or restore from a
backup; do not delete catalog evidence to undo a failed import.
