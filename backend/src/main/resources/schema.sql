CREATE TABLE IF NOT EXISTS vk_image_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT,
  url VARCHAR(1024) NOT NULL,
  hash VARCHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP,
  synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  ml_decision VARCHAR(32),
  ml_score DOUBLE,
  ml_reason VARCHAR(1024),
  use_for_training BOOLEAN DEFAULT TRUE
);

ALTER TABLE vk_image_history ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE vk_image_history ADD COLUMN IF NOT EXISTS ml_decision VARCHAR(32);
ALTER TABLE vk_image_history ADD COLUMN IF NOT EXISTS ml_score DOUBLE;
ALTER TABLE vk_image_history ADD COLUMN IF NOT EXISTS ml_reason VARCHAR(1024);
ALTER TABLE vk_image_history ADD COLUMN IF NOT EXISTS use_for_training BOOLEAN DEFAULT TRUE;
UPDATE vk_image_history SET use_for_training = TRUE WHERE use_for_training IS NULL;

CREATE INDEX IF NOT EXISTS idx_vk_image_history_use_training_created ON vk_image_history (use_for_training, created_at);

CREATE TABLE IF NOT EXISTS ml_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  candidate_id BIGINT,
  url VARCHAR(1024) NOT NULL,
  hash VARCHAR(255) NOT NULL,
  decision VARCHAR(16) NOT NULL,
  score DOUBLE,
  reason VARCHAR(1024),
  zone VARCHAR(8),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ml_feedback_hash ON ml_feedback (hash);
