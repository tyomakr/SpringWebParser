CREATE TABLE media_assets (
  id UUID PRIMARY KEY,
  attachment_id UUID NOT NULL UNIQUE REFERENCES attachments(id) ON DELETE CASCADE,
  source_kind TEXT NOT NULL,
  external_media_id TEXT NOT NULL,
  external_post_id TEXT,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  published_at TIMESTAMPTZ,
  mime_type TEXT NOT NULL,
  byte_size BIGINT NOT NULL CHECK (byte_size > 0),
  sha256 TEXT NOT NULL,
  content_bytes BYTEA NOT NULL,
  rights_basis TEXT NOT NULL,
  provenance_json TEXT NOT NULL,
  imported_at TIMESTAMPTZ NOT NULL,
  UNIQUE (source_kind, external_media_id)
);

CREATE INDEX idx_media_assets_sha256 ON media_assets(sha256);

CREATE TABLE media_analysis_runs (
  id UUID PRIMARY KEY,
  asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
  input_sha256 TEXT NOT NULL,
  analysis_version TEXT NOT NULL,
  hash_provider_version TEXT NOT NULL,
  phash_provider_version TEXT NOT NULL,
  text_provider_version TEXT NOT NULL,
  embedding_provider_version TEXT NOT NULL,
  phash BIGINT NOT NULL,
  embedding_json TEXT NOT NULL,
  text_ratio DOUBLE PRECISION NOT NULL CHECK (text_ratio >= 0 AND text_ratio <= 1),
  text_role TEXT NOT NULL,
  text_dominant BOOLEAN NOT NULL,
  explanation_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (asset_id, analysis_version, input_sha256)
);

CREATE INDEX idx_media_analysis_runs_asset_created
  ON media_analysis_runs(asset_id, created_at DESC);

ALTER TABLE recommendation_image_features
  ADD COLUMN analysis_version TEXT NOT NULL DEFAULT 'legacy-url-v1',
  ADD COLUMN analysis_explanation_json TEXT;

ALTER TABLE recommendation_feedback
  ADD COLUMN serving_event_id UUID REFERENCES recommendation_serving_events(id),
  ADD COLUMN served_rank INTEGER CHECK (served_rank IS NULL OR served_rank > 0),
  ADD COLUMN note TEXT;

CREATE INDEX idx_recommendation_feedback_serving_event
  ON recommendation_feedback(serving_event_id, created_at);
