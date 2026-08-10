CREATE TABLE channel_profiles (
  id UUID PRIMARY KEY,
  platform TEXT NOT NULL CHECK (BTRIM(platform) <> ''),
  external_channel_id TEXT NOT NULL CHECK (BTRIM(external_channel_id) <> ''),
  display_name TEXT NOT NULL CHECK (BTRIM(display_name) <> ''),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (platform, external_channel_id)
);

CREATE TABLE publication_occurrences (
  id UUID PRIMARY KEY,
  media_asset_id UUID NOT NULL REFERENCES catalog_media_assets(id) ON DELETE RESTRICT,
  channel_profile_id UUID NOT NULL REFERENCES channel_profiles(id) ON DELETE RESTRICT,
  external_publication_id TEXT NOT NULL CHECK (BTRIM(external_publication_id) <> ''),
  published_at TIMESTAMPTZ NOT NULL,
  confirmation_source TEXT NOT NULL CHECK (BTRIM(confirmation_source) <> ''),
  confirmed_at TIMESTAMPTZ NOT NULL,
  UNIQUE (channel_profile_id, external_publication_id)
);

CREATE INDEX idx_publication_occurrences_asset_channel
  ON publication_occurrences(media_asset_id, channel_profile_id, published_at DESC, id);

CREATE TABLE channel_eligibility_decisions (
  id UUID PRIMARY KEY,
  media_asset_id UUID NOT NULL REFERENCES catalog_media_assets(id) ON DELETE RESTRICT,
  channel_profile_id UUID NOT NULL REFERENCES channel_profiles(id) ON DELETE RESTRICT,
  decision TEXT NOT NULL CHECK (decision IN ('ALLOW', 'EXCLUDE')),
  reason TEXT NOT NULL CHECK (BTRIM(reason) <> ''),
  reason_detail TEXT,
  supersedes_decision_id UUID UNIQUE
    REFERENCES channel_eligibility_decisions(id) ON DELETE RESTRICT,
  decided_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_channel_eligibility_latest
  ON channel_eligibility_decisions(
    media_asset_id,
    channel_profile_id,
    decided_at DESC,
    id DESC
  );

CREATE TABLE channel_history_memberships (
  id UUID PRIMARY KEY,
  media_asset_id UUID NOT NULL REFERENCES catalog_media_assets(id) ON DELETE RESTRICT,
  channel_profile_id UUID NOT NULL REFERENCES channel_profiles(id) ON DELETE RESTRICT,
  publication_occurrence_id UUID NOT NULL
    REFERENCES publication_occurrences(id) ON DELETE RESTRICT,
  eligibility_decision_id UUID
    REFERENCES channel_eligibility_decisions(id) ON DELETE RESTRICT,
  active BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (media_asset_id, channel_profile_id)
);

CREATE INDEX idx_channel_history_active
  ON channel_history_memberships(channel_profile_id, active, updated_at DESC, id);
