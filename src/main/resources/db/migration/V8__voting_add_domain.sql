CREATE TABLE IF NOT EXISTS voting_sessions (
  id                                   TEXT PRIMARY KEY,
  board_id                             TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  scope_type                           TEXT NOT NULL,
  scope_ref                            TEXT NULL,
  state                                TEXT NOT NULL,
  created_by_user_id                   TEXT NOT NULL,
  allow_viewer_participation           BOOLEAN NOT NULL DEFAULT TRUE,
  allow_published_reader_participation BOOLEAN NOT NULL DEFAULT FALSE,
  max_votes_per_participant            INTEGER NOT NULL,
  anonymous_votes                      BOOLEAN NOT NULL DEFAULT TRUE,
  show_progress_during_voting          BOOLEAN NOT NULL DEFAULT FALSE,
  allow_vote_updates                   BOOLEAN NOT NULL DEFAULT FALSE,
  duration_seconds                     BIGINT NULL,
  created_at                           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                           TIMESTAMPTZ NOT NULL DEFAULT now(),
  opened_at                            TIMESTAMPTZ NULL,
  closed_at                            TIMESTAMPTZ NULL,
  revealed_at                          TIMESTAMPTZ NULL,
  CONSTRAINT chk_voting_sessions_scope_type CHECK (scope_type IN ('board', 'page', 'section', 'object_set', 'explicit_target')),
  CONSTRAINT chk_voting_sessions_state CHECK (state IN ('draft', 'open', 'closed', 'revealed', 'cancelled')),
  CONSTRAINT chk_voting_sessions_max_votes CHECK (max_votes_per_participant > 0),
  CONSTRAINT chk_voting_sessions_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0),
  CONSTRAINT chk_voting_sessions_scope_ref CHECK (
    (scope_type = 'board' AND scope_ref IS NOT NULL)
    OR (scope_type <> 'board' AND scope_ref IS NOT NULL AND length(trim(scope_ref)) > 0)
  )
);

CREATE INDEX IF NOT EXISTS idx_voting_sessions_board_id ON voting_sessions(board_id);
CREATE INDEX IF NOT EXISTS idx_voting_sessions_board_state ON voting_sessions(board_id, state, updated_at DESC);

CREATE TABLE IF NOT EXISTS vote_records (
  id                TEXT PRIMARY KEY,
  voting_session_id TEXT NOT NULL REFERENCES voting_sessions(id) ON DELETE CASCADE,
  participant_id    TEXT NOT NULL,
  target_ref        TEXT NOT NULL,
  vote_value        INTEGER NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_vote_records_value CHECK (vote_value > 0)
);

CREATE INDEX IF NOT EXISTS idx_vote_records_session_id ON vote_records(voting_session_id);
CREATE INDEX IF NOT EXISTS idx_vote_records_session_participant ON vote_records(voting_session_id, participant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_vote_records_session_participant_target ON vote_records(voting_session_id, participant_id, target_ref);
