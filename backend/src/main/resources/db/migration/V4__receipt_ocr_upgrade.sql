-- Structured extraction fields stored after receipt-ocr LLM processing.
-- ocr_provider distinguishes which engine produced the result (RECEIPT_OCR_SERVICE | TESSERACT_FALLBACK).
ALTER TABLE receipts
    ADD COLUMN IF NOT EXISTS extracted_merchant VARCHAR(255),
    ADD COLUMN IF NOT EXISTS extracted_amount   DECIMAL(15, 2),
    ADD COLUMN IF NOT EXISTS extracted_date     DATE,
    ADD COLUMN IF NOT EXISTS line_items_json    TEXT,
    ADD COLUMN IF NOT EXISTS ocr_confidence     DECIMAL(4, 3),
    ADD COLUMN IF NOT EXISTS ocr_provider       VARCHAR(30);
