CREATE TABLE IF NOT EXISTS saved_selections (
  id UUID PRIMARY KEY,
  username TEXT NOT NULL,
  item_id UUID NOT NULL,
  attachment_ids TEXT NOT NULL,
  target TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saved_selections_username ON saved_selections(username);
CREATE INDEX IF NOT EXISTS idx_saved_selections_expires ON saved_selections(expires_at);
