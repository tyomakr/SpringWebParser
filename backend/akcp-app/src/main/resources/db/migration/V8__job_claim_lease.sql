ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS claim_token UUID;

ALTER TABLE jobs
  ADD CONSTRAINT chk_jobs_attempt_count_non_negative
  CHECK (attempt_count >= 0);

CREATE INDEX IF NOT EXISTS idx_jobs_claimable
  ON jobs(status, lease_until, created_at);
