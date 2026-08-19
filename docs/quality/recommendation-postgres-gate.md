# Recommendation PostgreSQL integration gate

`RecommendationRepositoryPostgresIT` is a test-only gate for the existing
Flyway head. It creates a disposable PostgreSQL 16 container, applies all
tracked migrations, seeds one authorized fixture row and verifies:

- the backfill query exposes persisted `media_analysis_runs` data;
- the extractor prefers the persisted SHA/pHash/embedding/text decision;
- an existing versioned feature row is not selected for replacement;
- changing that row to `legacy-url-v1` makes it eligible for re-analysis.

The test does not call a platform API, download media, publish content or
change production configuration. It does not add a migration. The existing
legacy positive-history bias and role-based authorization decisions remain
outside this gate.

Run it with:

```text
mvn -pl backend/akcp-app -am "-Dtest=RecommendationRepositoryPostgresIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Docker must be running. If the daemon is unavailable, report the environment
failure rather than weakening the test or substituting an in-memory database.
