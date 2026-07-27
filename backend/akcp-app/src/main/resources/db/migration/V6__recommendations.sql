CREATE TABLE IF NOT EXISTS recommendation_image_features (
  id UUID PRIMARY KEY,
  dataset TEXT NOT NULL,
  attachment_id UUID REFERENCES attachments(id) ON DELETE CASCADE,
  image_url TEXT NOT NULL,
  sha256 TEXT,
  phash BIGINT,
  embedding_json TEXT,
  text_ratio DOUBLE PRECISION,
  text_dominant BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recommendation_image_features_attachment
  ON recommendation_image_features(attachment_id)
  WHERE attachment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_recommendation_image_features_dataset
  ON recommendation_image_features(dataset);

CREATE UNIQUE INDEX IF NOT EXISTS uq_recommendation_image_features_dataset_url
  ON recommendation_image_features(dataset, image_url);

CREATE INDEX IF NOT EXISTS idx_recommendation_image_features_text_dominant
  ON recommendation_image_features(text_dominant);

CREATE TABLE IF NOT EXISTS recommendation_feedback (
  id UUID PRIMARY KEY,
  username TEXT NOT NULL,
  reference_attachment_id UUID,
  recommended_attachment_id UUID,
  action TEXT NOT NULL,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recommendation_feedback_user_created
  ON recommendation_feedback(username, created_at DESC);
