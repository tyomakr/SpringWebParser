# PE5 analysis worker: bounded benchmark slice

Status: additive contract and offline fixture worker; not wired into the
application, scheduler, Compose or production configuration.

## What is implemented

- `MediaAnalysisProvider` returns a versioned, immutable result containing the
  input SHA-256, dimensions, perceptual hash, visual vector, text evidence and
  explanations.
- `AnalysisArtifactManifest` requires a license, size and lowercase SHA-256
  for every local artifact and rejects HTTP(S) locations. A provider cannot
  advertise network access.
- `BoundedAnalysisWorker` reads at most a configured byte limit, processes one
  work item at a time, writes only a complete result, and returns per-item
  `CREATED`, `REUSED` or `FAILED` outcomes.
- The write key is `(asset_id, input_sha256, analysis_profile_version)`;
  replaying a batch is therefore safe when the store implements that key.
- `DeterministicPixelAnalysisProvider` is a fixture baseline. Its vector is a
  pixel descriptor, not a semantic embedding, and its text map is fixture
  evidence, not OCR. It exists to exercise the worker and benchmark harness
  without downloading a model.

## Deliberate non-goals

This slice does not add a job type, Spring bean, CLI entry point, Compose
profile, incoming port, model file, language pack, database migration or
publishing credential. The existing legacy analysis path remains the default.
Selecting Tesseract/OCR and a semantic image model requires a separate
artifact/license/checksum/benchmark decision. No worker may download artifacts
at runtime.

## Benchmark protocol

Use only a small authorized fixture set with rights and provenance recorded.
Run the fixture provider and the worker with concurrency `1` and a bounded
limit. Record:

1. item count, successful/reused/failed counts and deterministic rerun result;
2. wall-clock duration and throughput (items/minute);
3. peak process memory and encoded-byte limits;
4. provider artifact size, license and SHA-256 from the manifest;
5. manually reviewed near-duplicate pairs;
6. text-area/role cases around the proposed threshold, including acceptable
   text that must not be excluded.

The fixture baseline can prove repeatability and failure handling only. It
cannot establish semantic retrieval quality, OCR recall or production
resource requirements. Those require a labeled authorized sample and an
approved provider artifact.

## Operational boundary and rollback

The worker is a one-shot library component. A future launcher must run it
outside a web request, with no inbound network port, concurrency `1`, bounded
temporary storage and read-only model mounts. A cursor/progress source and
orphan reconciliation are required before importing a large corpus or adding
unattended scheduling.

Rollback is a code revert while this component is unwired. Existing analysis
results and the legacy read path remain untouched. Enabling a persistent
provider later requires forward-only corrective migrations or restore from a
backup; do not delete analysis evidence as a rollback shortcut.
