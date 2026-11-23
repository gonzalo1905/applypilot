-- Personal access tokens: long-lived, read-only credentials for the MCP server, so Claude
-- Desktop or Claude Code can read a user's applications without the 60-minute login token.
-- Only a SHA-256 hash is stored; the token itself is shown once, at creation.
CREATE TABLE personal_access_tokens (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(60)   NOT NULL,
    token_hash    CHAR(64)      NOT NULL,
    -- The first characters, so the user can tell tokens apart without the secret part.
    token_prefix  VARCHAR(16)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT uk_personal_access_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_personal_access_tokens_user ON personal_access_tokens (user_id);
