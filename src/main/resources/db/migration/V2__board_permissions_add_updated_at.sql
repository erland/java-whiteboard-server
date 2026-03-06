-- Add updated_at to board_permissions for existing installations.
-- New installations get the column from V1__baseline.sql.

ALTER TABLE board_permissions
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Best-effort backfill (in case the column was added manually as nullable).
UPDATE board_permissions
  SET updated_at = COALESCE(updated_at, created_at, now());
