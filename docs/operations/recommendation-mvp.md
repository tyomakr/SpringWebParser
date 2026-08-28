# Recommendation MVP: human-in-the-loop vertical slice

This block adds a deterministic, reviewable recommendation slice for small
authorized fixture/export datasets. It does not add VK/Instagram/Telegram
network clients, model downloads, publishing, auto-publishing, or a database
migration.

## Included behavior

- candidate and published-history features are stored through the existing
  recommendation tables and versioned by analysis/ranking identifiers. The
  backfill consumes the latest persisted `media_analysis_runs` row (SHA/pHash,
  embedding, text ratio/role decision and explanation);
- exact SHA-256 and bounded perceptual-hash proximity are used for duplicate
  suppression;
- text-area policy is configurable and produces a typed `TEXT_DOMINANT`
  exclusion with evidence rather than treating every OCR/text signal as a hard
  rejection. The current default is `0.65`, configurable as
  `akcp.recommendations.text-dominant-threshold`;
- top-k selection is deterministic, applies a diversity penalty, and stores a
  serving snapshot with component scores and similar published examples;
- moderation records only `APPROVE`, `REJECT`, or `SKIP` plus a controlled
  reason and optional note; no `PUBLISH` action exists in this contract;
- offline fixture metrics cover deduplication, text false exclusions,
  decision coverage, an acceptance-based precision@k proxy, and diversity.

Published history remains a biased positive sample. It is useful for similarity
and exclusion context, but it is not a complete label set and does not prove
publication success.

## Safety boundary

The API is currently an authenticated application surface. Role-specific
moderator authorization is a separate decision because the existing security
configuration is outside this block. The UI is a manual queue only. There is
no live platform API call and no publishing queue transition.

The fixture analysis uses deterministic byte/pixel descriptors. They are
contract fixtures, not semantic embeddings or OCR. Legacy URL-derived rows are
kept for compatibility but fail closed and are excluded from ranking until a
versioned byte-analysis row exists. A production provider must be selected only
after an authorized data sample, reproducible benchmark, and explicit review of
model/runtime requirements.

## Verification and rollback

Run the library tests and the UI production build before merging. A clean full
application suite may additionally require a running PostgreSQL/Testcontainers
daemon. If the block is not acceptable, revert its commit; this removes code
and documentation without deleting catalog data. Any already-persisted
recommendation data requires a separately approved backup/forward-migration
procedure, not an ad-hoc delete.

## Next evidence gate

Before adding a provider or publishing preparation, collect moderator
`APPROVE/REJECT/SKIP` decisions with reasons, measure precision@k proxy,
false text exclusions, diversity and time saved, and inspect role/audit
boundaries. Automatic publication requires a separate ADR and explicit
approval.
