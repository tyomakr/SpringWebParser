CREATE TABLE IF NOT EXISTS jobs (
  id UUID PRIMARY KEY,
  type TEXT NOT NULL,
  status TEXT NOT NULL,
  payload TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
