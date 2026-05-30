CREATE TABLE transactions (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount           DECIMAL(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'INR',
    transaction_date DATE          NOT NULL,
    description      TEXT          NOT NULL,
    merchant         VARCHAR(255),
    category         VARCHAR(100),
    subcategory      VARCHAR(100),
    type             VARCHAR(10)   NOT NULL,   -- DEBIT | CREDIT
    source           VARCHAR(20)   NOT NULL,   -- MANUAL | CSV | SMS | OCR
    confidence_score DECIMAL(5,4),             -- 0.0000 – 1.0000
    raw_text         TEXT,                     -- original unparsed line
    notes            TEXT,
    is_reviewed      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Primary access pattern: user's transactions newest-first
CREATE INDEX idx_transactions_user_date     ON transactions(user_id, transaction_date DESC);
-- Category-level aggregation queries (Phases 6–7)
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category);

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
