-- Baseline schema for java-whiteboard-server
-- Notes:
-- - IDs are stored as TEXT to stay compatible with client-generated UUID strings.
-- - Snapshots are stored as JSONB.
-- - Ops are optional for MVP but included for future audit/replay.

CREATE TABLE IF NOT EXISTS boards (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  type          TEXT NOT NULL,
  owner_user_id TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'active', -- active | archived | deleted
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_boards_owner_user_id ON boards(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_boards_updated_at ON boards(updated_at);

-- Optional: explicit sharing to authenticated users (phase 2)
CREATE TABLE IF NOT EXISTS board_permissions (
  board_id   TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  user_id    TEXT NOT NULL,
  role       TEXT NOT NULL, -- viewer | editor
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (board_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_board_permissions_user_id ON board_permissions(user_id);

-- Invite tokens: store ONLY a hash, never the raw token.
CREATE TABLE IF NOT EXISTS invites (
  id             TEXT PRIMARY KEY, -- server-side identifier for management
  token_hash     TEXT NOT NULL UNIQUE,
  board_id       TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  permission     TEXT NOT NULL, -- viewer | editor
  expires_at     TIMESTAMPTZ NULL,
  max_uses       INTEGER NULL,
  uses           INTEGER NOT NULL DEFAULT 0,
  revoked_at     TIMESTAMPTZ NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invites_board_id ON invites(board_id);
CREATE INDEX IF NOT EXISTS idx_invites_token_hash ON invites(token_hash);

-- Latest snapshots (versioned)
CREATE TABLE IF NOT EXISTS board_snapshots (
  board_id     TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  version      BIGINT NOT NULL,
  snapshot     JSONB NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   TEXT NULL,
  PRIMARY KEY (board_id, version)
);

-- Convenient pointer to latest snapshot version per board
CREATE TABLE IF NOT EXISTS board_snapshot_latest (
  board_id          TEXT PRIMARY KEY REFERENCES boards(id) ON DELETE CASCADE,
  latest_version    BIGINT NOT NULL,
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Optional append-only operation log
CREATE TABLE IF NOT EXISTS board_ops (
  board_id    TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  seq         BIGINT NOT NULL,
  op          JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id TEXT NULL,
  PRIMARY KEY (board_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_board_ops_created_at ON board_ops(created_at);
