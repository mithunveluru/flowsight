-- Replace the subscription/tier system with a simple per-user receipt quota.
-- All platform features are now available to every user; only OCR receipt
-- processing is gated by a configurable quota.

-- 1) Drop tier-related columns (added in V8 — never used in production for billing)
ALTER TABLE users
    DROP COLUMN IF EXISTS subscription_tier,
    DROP COLUMN IF EXISTS subscription_started_at,
    DROP COLUMN IF EXISTS subscription_expires_at;

-- 2) Add receipt quota columns
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS receipt_limit       INT     NOT NULL DEFAULT 50,
    ADD COLUMN IF NOT EXISTS receipts_processed  INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unlimited_receipts  BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Backfill receipts_processed with current count from receipts table
UPDATE users u
SET    receipts_processed = (
    SELECT COUNT(*) FROM receipts r WHERE r.user_id = u.id
);
