-- Phase 12: AI-powered Financial Intelligence Reports.
-- Each row represents one PDF generation request. The PDF blob is stored on disk
-- under UPLOAD_DIR/reports/{userId}/{jobId}.pdf and referenced here by path.
CREATE TABLE report_jobs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_start     DATE         NOT NULL,
    period_end       DATE         NOT NULL,
    period_label     VARCHAR(80)  NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',  -- PENDING|GENERATING|READY|FAILED
    pdf_path         VARCHAR(500),
    pdf_size_bytes   BIGINT,
    error_message    TEXT,
    download_count   INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

CREATE INDEX idx_report_jobs_user_id    ON report_jobs(user_id);
CREATE INDEX idx_report_jobs_created_at ON report_jobs(created_at DESC);
