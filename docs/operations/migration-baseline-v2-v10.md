# Flyway migration baseline V2-V10

## Status and intent

V2-V10 are legacy-adoption migrations recovered from the working project.
They establish the schema expected by the existing jobs, integrations, saved
selection and recommendation code. They are frozen as an ordered prerequisite
for later migrations; V11 and later changes are not part of this baseline.

V2 is intentionally a no-op after the tracked V1 schema because V1 already
creates `jobs` and `idx_jobs_status`. V3-V7 use `IF NOT EXISTS` to adopt
development databases that may already contain those objects. This makes a
clean migration safe, but it does not prove that an independently created
object has the expected columns and constraints.

## Mandatory gate before deployment to an existing database

Do not deploy this chain to an unknown database from Git history alone.

1. Take a tested database backup.
2. Record Flyway history without copying application secrets:

   ```sql
   SELECT installed_rank, version, description, checksum, installed_on, success
   FROM flyway_schema_history
   ORDER BY installed_rank;
   ```

3. Inventory the adopted tables:

   ```sql
   SELECT table_name, column_name, data_type, is_nullable, column_default
   FROM information_schema.columns
   WHERE table_schema = current_schema()
     AND table_name IN (
       'jobs',
       'user_integrations',
       'saved_selections',
       'recommendation_image_features',
       'recommendation_feedback',
       'recommendation_serving_events',
       'media_assets',
       'media_analysis_runs'
     )
   ORDER BY table_name, ordinal_position;
   ```

   Inventory constraints and indexes as well:

   ```sql
   SELECT tc.table_name, tc.constraint_name, tc.constraint_type,
          kcu.column_name, kcu.ordinal_position
   FROM information_schema.table_constraints tc
   LEFT JOIN information_schema.key_column_usage kcu
     ON kcu.constraint_schema = tc.constraint_schema
    AND kcu.constraint_name = tc.constraint_name
   WHERE tc.table_schema = current_schema()
   ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position;

   SELECT tablename, indexname, indexdef
   FROM pg_indexes
   WHERE schemaname = current_schema()
   ORDER BY tablename, indexname;

   SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
   FROM pg_constraint
   WHERE connamespace = current_schema()::regnamespace
   ORDER BY conrelid::regclass::text, conname;
   ```

4. Compare the columns, constraints and indexes with V1-V10 before running
   Flyway. In particular,
   verify columns that `IF NOT EXISTS` cannot repair on a drifted table:
   `user_integrations.config_json`, `user_integrations.last_verified_at`,
   `saved_selections.target`, and the recommendation feature/feedback fields.
   Verify primary/unique/foreign-key constraints and index definitions,
   including `user_integrations(username, provider)` and the V8 jobs check.
5. Against a restored copy, run Flyway migration explicitly to target version
   `10`, then run `flyway validate`. Validation alone does not execute pending
   migrations or reveal a DDL-name collision. A checksum mismatch, failed
   migration, missing column, incompatible type or incompatible constraint
   requires a new forward corrective migration; never edit an already applied
   migration.
6. Apply to production only in a separately approved maintenance window.

## Automated evidence

`MigrationChainV2ToV10Test` uses PostgreSQL 16 and explicit Flyway targets. It
checks:

- a seeded V1 database upgraded exactly to V10;
- preservation of legacy item/job values and V8/V9 defaults;
- a seeded V9 feedback record upgraded exactly to V10;
- presence of the expected V6/V7/V10 tables;
- `flyway validate` after each upgrade.

These tests validate clean and controlled upgrade paths. They do not replace
the inventory gate for a database whose migration history is unknown.
