# Task 6 offline fixture report

This report describes the deterministic golden values asserted by
`OfflineMetricsCalculatorTest`. It is not a production-quality measurement.

| Metric | Golden fixture value |
|---|---:|
| Import total / imported / replayed / failed | 6 / 3 / 2 / 1 |
| Exact dedup precision / recall | 0.666667 / 0.666667 |
| Near dedup precision / recall | 0.75 / 0.75 |
| False text-exclusion rate | 0.25 |
| Decision coverage | 0.8 |
| Acceptance among approve/reject | 0.666667 |
| Precision@3 proxy | 0.333333 |
| Intra-list diversity | 0.666667 |

These values test calculation correctness only. They do not establish useful
thresholds, model quality or expected production performance. The next
measurement must use an authorized sample and independently labeled duplicate,
text-role and relevance judgments.

The metrics input now requires a complete served snapshot with unique,
continuous ranks starting at 1; undecided rows must be included. This prevents
`precision@k proxy` from silently using only the subset that received feedback.

The connected PostgreSQL/Testcontainers wiring path is a separate integration
gate and is not represented by these pure calculator tests. The synthetic
observations here remain contract evidence, not a product-quality baseline.
