-- V13: Rotating refresh tokens.
-- Access tokens are now short-lived (15 min); long-lived sessions are carried by
-- opaque refresh tokens stored here as SHA-256 hashes only. Rotation: each use
-- consumes the row and issues a new one. Reuse of a consumed/revoked token is
-- treated as theft and revokes every live token for that user.

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
