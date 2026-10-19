-- Where a transaction's category came from and what the model offered instead.
-- Nullable without defaults: metadata-only on Postgres, no rewrite or backfill of existing rows.
ALTER TABLE transactions ADD COLUMN category_source VARCHAR(20);
ALTER TABLE transactions ADD COLUMN category_model_version VARCHAR(64);
ALTER TABLE transactions ADD COLUMN category_alternatives JSONB;
