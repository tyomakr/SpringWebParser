CREATE TABLE items (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  content TEXT,
  source_type TEXT,
  source_url TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE attachments (
  id UUID PRIMARY KEY,
  item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  url TEXT NOT NULL,
  metadata TEXT
);

CREATE TABLE tags (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE item_tags (
  item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (item_id, tag_id)
);

CREATE TABLE jobs (
  id UUID PRIMARY KEY,
  type TEXT NOT NULL,
  status TEXT NOT NULL,
  payload TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  last_error TEXT
);

CREATE INDEX idx_items_created_at ON items(created_at DESC);
CREATE INDEX idx_attachments_item_id ON attachments(item_id);
CREATE INDEX idx_item_tags_tag_id ON item_tags(tag_id);
CREATE INDEX idx_jobs_status ON jobs(status);
