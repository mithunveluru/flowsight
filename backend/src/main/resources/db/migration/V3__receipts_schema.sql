CREATE TABLE receipts (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(1000),
    file_size     BIGINT       NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    ocr_text      TEXT,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | FAILED
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipts_user_id ON receipts(user_id);

CREATE TRIGGER trg_receipts_updated_at
    BEFORE UPDATE ON receipts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Link transactions back to the receipt they were extracted from (nullable — most come from CSV/manual)
ALTER TABLE transactions
    ADD COLUMN receipt_id UUID REFERENCES receipts(id) ON DELETE SET NULL;
