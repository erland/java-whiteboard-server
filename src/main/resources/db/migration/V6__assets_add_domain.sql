CREATE TABLE assets (
    id VARCHAR(64) PRIMARY KEY,
    board_id VARCHAR(64),
    scope_type VARCHAR(32) NOT NULL,
    scope_ref VARCHAR(255) NOT NULL,
    logical_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    integrity_hash VARCHAR(512),
    version_tag VARCHAR(128),
    state VARCHAR(32) NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    failure_reason VARCHAR(1000),
    CONSTRAINT fk_assets_board FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE,
    CONSTRAINT chk_assets_scope_type CHECK (scope_type IN ('board', 'user_private', 'library')),
    CONSTRAINT chk_assets_state CHECK (state IN ('pending', 'active', 'failed', 'deleted', 'quarantined')),
    CONSTRAINT chk_assets_size_bytes CHECK (size_bytes >= 0),
    CONSTRAINT chk_assets_board_scope CHECK (
        (scope_type = 'board' AND board_id IS NOT NULL AND scope_ref = board_id)
        OR (scope_type <> 'board')
    )
);

CREATE INDEX idx_assets_board_id ON assets(board_id);
CREATE INDEX idx_assets_scope ON assets(scope_type, scope_ref);
CREATE INDEX idx_assets_state ON assets(state);
