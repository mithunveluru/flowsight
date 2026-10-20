-- Global accept/dismiss counts per recommendation kind: the prior for per-user ranking.
-- Per-user feedback lives in user_feedback (subject_type RECOMMENDATION). Only users with
-- personalization on contribute here. New empty table, no backfill.
CREATE TABLE recommendation_kind_stats (
    kind       VARCHAR(60) PRIMARY KEY,
    accepted   BIGINT      NOT NULL DEFAULT 0,
    dismissed  BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
