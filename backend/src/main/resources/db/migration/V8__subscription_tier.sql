-- Phase 11: SaaS subscription tier per user.
-- FREE is the default; tier-based limits are enforced at the service layer.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS subscription_tier       VARCHAR(30) NOT NULL DEFAULT 'FREE',
    ADD COLUMN IF NOT EXISTS subscription_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ;
