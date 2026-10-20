-- What the recurring-scorer said about a pattern; all NULL for patterns the rules detected.
-- Nullable or constant defaults only: metadata-only on Postgres, no rewrite of existing rows.
ALTER TABLE recurring_patterns ADD COLUMN probability DECIMAL(5, 4);
ALTER TABLE recurring_patterns ADD COLUMN next_expected_low DATE;
ALTER TABLE recurring_patterns ADD COLUMN next_expected_high DATE;
ALTER TABLE recurring_patterns ADD COLUMN model_version VARCHAR(64);
-- 2 when one service is billed twice per period (two accounts); estimated_amount covers both
ALTER TABLE recurring_patterns ADD COLUMN charges_per_period INTEGER NOT NULL DEFAULT 1;
