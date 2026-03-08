CREATE TABLE IF NOT EXISTS comments (
  id                TEXT PRIMARY KEY,
  board_id          TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  parent_comment_id TEXT NULL REFERENCES comments(id) ON DELETE CASCADE,
  target_type       TEXT NOT NULL, -- board | object | region | comment
  target_ref        TEXT NOT NULL,
  author_user_id    TEXT NOT NULL,
  content           TEXT NOT NULL,
  state             TEXT NOT NULL DEFAULT 'active', -- active | resolved | deleted
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at       TIMESTAMPTZ NULL,
  deleted_at        TIMESTAMPTZ NULL,
  CONSTRAINT chk_comments_target_type CHECK (target_type IN ('board', 'object', 'region', 'comment')),
  CONSTRAINT chk_comments_state CHECK (state IN ('active', 'resolved', 'deleted')),
  CONSTRAINT chk_comments_reply_consistency CHECK (
    (target_type = 'comment' AND parent_comment_id IS NOT NULL AND target_ref = parent_comment_id) OR
    (target_type <> 'comment' AND parent_comment_id IS NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_comments_board_id ON comments(board_id);
CREATE INDEX IF NOT EXISTS idx_comments_parent_comment_id ON comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_comments_state ON comments(state);
