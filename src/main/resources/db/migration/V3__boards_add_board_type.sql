ALTER TABLE boards
  ADD COLUMN IF NOT EXISTS board_type TEXT;

UPDATE boards
SET board_type = CASE
  WHEN board_type IS NOT NULL THEN board_type
  WHEN type IN ('advanced', 'freehand', 'mindmap') THEN type
  ELSE 'advanced'
END;

ALTER TABLE boards
  ALTER COLUMN board_type SET DEFAULT 'advanced';

ALTER TABLE boards
  ALTER COLUMN board_type SET NOT NULL;
