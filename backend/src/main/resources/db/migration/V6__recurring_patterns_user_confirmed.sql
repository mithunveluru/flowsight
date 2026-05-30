-- Phase 6 patch: user-confirmed flag lets users mark detected patterns as definitely recurring.
-- Confirmed patterns are preserved across re-scans even if confidence drops, and their
-- metadata (last seen, next expected) is refreshed on each scan.
ALTER TABLE recurring_patterns
    ADD COLUMN IF NOT EXISTS is_user_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
