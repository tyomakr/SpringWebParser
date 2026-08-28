CREATE TABLE IF NOT EXISTS recommendation_serving_events (
  id UUID PRIMARY KEY,
  username TEXT NOT NULL,
  reference_attachment_id UUID NOT NULL,
  experiment_group TEXT NOT NULL,
  requested_limit INTEGER NOT NULL,
  returned_count INTEGER NOT NULL,
  candidates_json TEXT,
  latency_ms BIGINT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recommendation_serving_events_user_created
  ON recommendation_serving_events(username, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_recommendation_serving_events_reference
  ON recommendation_serving_events(reference_attachment_id, created_at DESC);
