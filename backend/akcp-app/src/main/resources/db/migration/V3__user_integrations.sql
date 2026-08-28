CREATE TABLE IF NOT EXISTS user_integrations (
  username TEXT NOT NULL,
  provider TEXT NOT NULL,
  secret_enc TEXT NOT NULL,
  config_json TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (username, provider)
);

CREATE INDEX IF NOT EXISTS idx_user_integrations_provider ON user_integrations(provider);
