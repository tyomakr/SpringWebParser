# Job claim/lease rollout

Scope: rollout of Flyway `V8__job_claim_lease.sql`,
`V9__job_external_outcome.sql` and the matching application binary. This runbook
does not authorize a production deployment or manual job state changes.

## Safety boundary

- Take a verified PostgreSQL backup before deployment.
- Apply V8 in a maintenance window with every job writer stopped. Do not use a
  rolling mixed-version deployment.
- Measure the `jobs` table and review lock timeout before applying the migration.
- Keep publishing disabled or manually gated during rollout.
- Set `AKCP_JOBS_LEASE_DURATION` longer than the observed maximum handler
  duration. Default: `PT30M`.
- V8 and V9 are additive: claim counters/lease fields, `external_result`, a
  check constraint and a claim lookup index.

## Read-only preflight

Run count-only inventory; do not print payloads or credentials:

```sql
SELECT status, COUNT(*)
FROM jobs
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS legacy_in_progress
FROM jobs
WHERE status = 'IN_PROGRESS';
```

Record counts, application version, backup identifier and the operator decision
for every existing `IN_PROGRESS` job.

## Verification after migration

Verify that two workers cannot acquire the same queued job, claim attempts are
counted, terminal outcomes clear lease/token, expired non-publishing claims can
be reacquired, and expired `PUBLISH_VK` claims require reconciliation.

No live VK post is permitted as a rollout test.

## Rollback

- Stop all new workers before starting an old binary.
- Do not drop V8 columns; the old binary ignores additive columns.
- Inventory `IN_PROGRESS` rows and lease state with count-only queries.
- Requeue or terminal-state changes require an explicit per-job operator decision.
- Preserve the backup and Flyway history. Never edit an applied migration.

Known limitation: there is no lease heartbeat. A long non-publishing handler can
be reclaimed. `PUBLISH_VK` remains excluded from automatic reclaim because
transport ambiguity requires explicit reconciliation.
