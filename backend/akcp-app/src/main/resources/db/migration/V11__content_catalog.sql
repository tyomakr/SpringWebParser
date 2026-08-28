CREATE TABLE catalog_media_assets (
  id UUID PRIMARY KEY,
  sha256 TEXT NOT NULL UNIQUE
    CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  mime_type TEXT NOT NULL
    CHECK (BTRIM(mime_type) <> ''),
  width INTEGER NOT NULL
    CHECK (width > 0),
  height INTEGER NOT NULL
    CHECK (height > 0),
  storage_reference TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (
    storage_reference =
      'sha256/' ||
      SUBSTRING(sha256 FROM 1 FOR 2) || '/' ||
      SUBSTRING(sha256 FROM 3 FOR 2) || '/' ||
      sha256
  )
);

CREATE TABLE catalog_source_occurrences (
  id UUID PRIMARY KEY,
  media_asset_id UUID NOT NULL
    REFERENCES catalog_media_assets(id) ON DELETE RESTRICT,
  platform TEXT NOT NULL
    CHECK (BTRIM(platform) <> ''),
  source_connection_id TEXT NOT NULL DEFAULT '',
  source_record_id TEXT NOT NULL
    CHECK (BTRIM(source_record_id) <> ''),
  external_post_id TEXT,
  external_media_id TEXT,
  post_url TEXT,
  media_url TEXT,
  metadata TEXT,
  discovered_at TIMESTAMPTZ NOT NULL,
  UNIQUE (platform, source_connection_id, source_record_id)
);

CREATE INDEX idx_catalog_source_occurrences_asset
  ON catalog_source_occurrences(media_asset_id, discovered_at, id);
