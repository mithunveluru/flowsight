CREATE TABLE recurring_patterns (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    merchant             VARCHAR(255) NOT NULL,
    normalized_key       VARCHAR(255) NOT NULL,
    period               VARCHAR(30)  NOT NULL,
    estimated_amount     DECIMAL(15, 4),
    last_seen_date       DATE,
    next_expected_date   DATE,
    occurrence_count     INT          NOT NULL DEFAULT 0,
    confidence           DECIMAL(4, 3),
    is_cancellation_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    is_dismissed         BOOLEAN      NOT NULL DEFAULT FALSE,
    detected_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_patterns_user_id ON recurring_patterns(user_id);

-- Prevents duplicate patterns per user; allows safe UPSERT by key
CREATE UNIQUE INDEX idx_recurring_patterns_user_key_period
    ON recurring_patterns(user_id, normalized_key, period);

CREATE TRIGGER trg_recurring_patterns_updated_at
    BEFORE UPDATE ON recurring_patterns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
