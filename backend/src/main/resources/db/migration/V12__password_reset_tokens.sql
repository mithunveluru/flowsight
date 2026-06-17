-- V12: Password reset tokens.
-- Stores only the SHA-256 hash of the issued token; the raw token never
-- touches the database. Lookups happen by token_hash, never by raw token.

CREATE TABLE password_reset_tokens (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    requesting_ip VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
