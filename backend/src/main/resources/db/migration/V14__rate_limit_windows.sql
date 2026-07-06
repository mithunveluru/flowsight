-- V14: Shared rate-limit state.
-- Fixed-window counters keyed by (bucket_key, window_start). Kept in Postgres so
-- limits hold across app instances and restarts; rows are purged periodically.

CREATE TABLE rate_limit_windows (
    bucket_key   VARCHAR(160) NOT NULL,
    window_start TIMESTAMPTZ  NOT NULL,
    count        INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (bucket_key, window_start)
);

CREATE INDEX idx_rate_limit_windows_window_start ON rate_limit_windows(window_start);
