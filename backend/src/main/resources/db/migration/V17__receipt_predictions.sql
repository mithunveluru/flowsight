-- V17: keep the OCR prediction after the user confirms, and store the receipt currency,
-- per-field confidence and the review decision. Additive, nullable columns only.
-- extracted_* keep their meaning (the confirmed values once confirmed); predicted_* never change
-- after OCR, so predicted vs confirmed can be compared offline.
ALTER TABLE receipts
    ADD COLUMN currency               VARCHAR(3),
    ADD COLUMN predicted_merchant     VARCHAR(255),
    ADD COLUMN predicted_merchant_raw VARCHAR(255),
    ADD COLUMN predicted_amount       DECIMAL(15, 2),
    ADD COLUMN predicted_date         DATE,
    ADD COLUMN predicted_currency     VARCHAR(3),
    ADD COLUMN merchant_confidence    DECIMAL(4, 3),
    ADD COLUMN amount_confidence      DECIMAL(4, 3),
    ADD COLUMN date_confidence        DECIMAL(4, 3),
    ADD COLUMN currency_confidence    DECIMAL(4, 3),
    ADD COLUMN ocr_decision           VARCHAR(30),
    ADD COLUMN ocr_model_version      VARCHAR(64),
    ADD COLUMN extraction_details     TEXT,
    ADD COLUMN confirmed_at           TIMESTAMPTZ;
