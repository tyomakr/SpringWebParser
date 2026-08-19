# Task 6 recommendation fixture slice

Scope: a small, authorized, offline dataset used to validate the recommendation
pipeline. This runbook does not authorize VK access, media scraping, model
downloads, publishing or production deployment.

## Import contract

The internal `AuthorizedMediaManifest` contract has `schemaVersion=1`,
`batchId`, one of the allowed `sourceKind` values
`VK_AUTHORIZED_EXPORT` / `CANDIDATE_FIXTURE`, and records containing:

- stable external media ID and optional external post ID;
- whether the image was already published and its timestamp;
- MIME type and base64-encoded image bytes;
- a non-empty rights basis and provenance description;
- optional fixture text-area ratio and compositional role.

The importer/catalog population step is a prerequisite owned by the preceding
authorized-ingestion block. This recommendation slice consumes rows that are
already present in the catalog; it adds no network-facing import endpoint and
never follows a URL. Replaying or conflict behavior belongs to that ingestion
contract and is not widened here.

## Storage and analysis

The recommendation tables consume versioned feature rows. The local fixture
baseline provides SHA-256, a pixel-derived 8x8 average perceptual hash, a
deterministic 4x4 RGB-grid descriptor, and fixture-supplied text-area/role
annotation. Backfill reads the latest persisted analysis row; legacy URL-derived
features are retained only for compatibility and fail closed in ranking.
Immutable byte storage and analysis-run persistence are owned by the
catalog/analysis block and are not changed here.

The RGB descriptor is not a semantic embedding. The text annotation is not
OCR inference. They validate provider/version/ranking contracts without
downloading a model. Production OCR and semantic embeddings remain separate
provider decisions requiring a labeled dataset and explicit approval.

## Human-in-the-loop boundary

Every recommendation response has a persisted run ID. Candidate snapshots
include rank, component scores, diversity penalty, analysis/ranking versions
and up to three published-history exemplars. The same immutable snapshot also
records typed exclusions (`TEXT_DOMINANT`, reference/history/candidate
duplicate, or missing compatible visual signal) with the measured evidence and
provider version. Snapshot serialization is fail-closed: a run ID is not
returned unless the full snapshot can be stored.

Exact/near duplicates are removed against the reference, published history and
the candidate set itself before diversity selection. Candidate-set
representatives are deterministic by attachment ID.

Moderation accepts only `APPROVE`, `REJECT`, or `SKIP`. It verifies that the
candidate and rank belong to the stated run. Reject/skip require a controlled
reason; the UI also permits a note. `PUBLISH` is not a recommendation decision.

## Offline metrics

`OfflineMetricsCalculatorTest` produces a deterministic in-memory report for
golden fixtures:

- import/replay/failure counts;
- exact and near-duplicate precision/recall;
- false exclusion rate for acceptable-text images;
- decision coverage, acceptance and precision@k proxy;
- intra-list diversity.

Precision@k is a moderator-acceptance proxy, not ground truth. Published
history is a biased positive sample and never serves as a complete “good”
label.

The offline calculator and the recommendation service contracts are verified in
the library test suite. A connected PostgreSQL/Testcontainers golden path is a
separate integration gate; it is intentionally not required to run without a
Docker daemon and is not part of this wiring-free block.

The authenticated manual feature endpoint validates SHA-256, text ratios and
finite bounded embeddings. Changing endpoint roles remains a separate auth
decision requiring explicit approval.

## Rollback

1. Stop fixture import and recommendation backfill.
2. Inventory `media_assets`, `media_analysis_runs`, serving events and linked
   decisions before changing data.
3. If V10 was applied, preserve its Flyway history and additive columns.
4. Recomputable fixture/analysis data may be deleted only through a separately
   reviewed, explicitly approved procedure.
5. Legacy V6 features remain readable and explicitly report
   `legacy-url-v1`; do not relabel them as pixel analysis.
