ALTER TABLE user_integrations
  ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMPTZ;
