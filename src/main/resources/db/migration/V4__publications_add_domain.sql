CREATE TABLE IF NOT EXISTS publications (
  id                TEXT PRIMARY KEY,
  board_id          TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  snapshot_version  BIGINT NULL,
  target_type       TEXT NOT NULL, -- board | snapshot
  state             TEXT NOT NULL DEFAULT 'active', -- inactive | active | revoked | expired
  access_token_hash TEXT NOT NULL UNIQUE,
  created_by_user_id TEXT NULL,
  allow_comments    BOOLEAN NOT NULL DEFAULT false,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at        TIMESTAMPTZ NULL,
  revoked_at        TIMESTAMPTZ NULL,
  CONSTRAINT chk_publications_target_type CHECK (target_type IN ('board', 'snapshot')),
  CONSTRAINT chk_publications_state CHECK (state IN ('inactive', 'active', 'revoked', 'expired')),
  CONSTRAINT chk_publications_snapshot_target CHECK (
    (target_type = 'board' AND snapshot_version IS NULL) OR
    (target_type = 'snapshot' AND snapshot_version IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_publications_board_id ON publications(board_id);
CREATE INDEX IF NOT EXISTS idx_publications_access_token_hash ON publications(access_token_hash);
CREATE INDEX IF NOT EXISTS idx_publications_state ON publications(state);
CREATE INDEX IF NOT EXISTS idx_publications_expires_at ON publications(expires_at);
