CREATE TABLE IF NOT EXISTS shared_timers (
  id                 TEXT PRIMARY KEY,
  board_id           TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  scope_type         TEXT NOT NULL,
  scope_ref          TEXT NULL,
  controller_user_id TEXT NOT NULL,
  label              TEXT NULL,
  state              TEXT NOT NULL,
  duration_ms        BIGINT NOT NULL,
  remaining_ms       BIGINT NOT NULL,
  started_at         TIMESTAMPTZ NULL,
  ends_at            TIMESTAMPTZ NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shared_timers_board_id ON shared_timers(board_id);
CREATE INDEX IF NOT EXISTS idx_shared_timers_board_state ON shared_timers(board_id, state, updated_at DESC);
