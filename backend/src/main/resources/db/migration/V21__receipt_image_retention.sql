-- V21: receipt images are kept only for review. file_path now holds the review copy's store key;
-- image_expires_at is when the cleanup job removes it, image_deleted_at when it was removed.
-- Additive, nullable columns only.
ALTER TABLE receipts
    ADD COLUMN image_expires_at TIMESTAMPTZ,
    ADD COLUMN image_deleted_at TIMESTAMPTZ;

-- Files stored before this are the untouched uploads (EXIF included) and are never served;
-- the first cleanup run removes them.
UPDATE receipts SET image_expires_at = now() WHERE file_path IS NOT NULL;

CREATE INDEX idx_receipts_image_expires_at ON receipts (image_expires_at)
    WHERE file_path IS NOT NULL AND image_deleted_at IS NULL;
