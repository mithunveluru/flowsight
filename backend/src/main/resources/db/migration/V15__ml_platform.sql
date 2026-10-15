-- V15: ML platform storage. Additive only: new tables plus one defaulted column.
-- User-owned rows cascade on account deletion.

ALTER TABLE users ADD COLUMN personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Model versions this deployment has received predictions from (catalog; the
-- ml-service manifest is the source of truth for status and metrics).
CREATE TABLE model_versions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    capability    VARCHAR(50)  NOT NULL,
    version       VARCHAR(64)  NOT NULL,
    first_seen_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_model_versions_capability_version UNIQUE (capability, version)
);

-- Served and shadow predictions. Holds a minimized output summary, never raw inputs.
-- Pruned by PredictionLogService after the retention window.
CREATE TABLE prediction_log (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         REFERENCES users(id) ON DELETE CASCADE,
    capability     VARCHAR(50)  NOT NULL,
    mode           VARCHAR(10)  NOT NULL,
    outcome        VARCHAR(20)  NOT NULL,
    model_version  VARCHAR(64),
    subject_type   VARCHAR(30),
    subject_id     UUID,
    confidence     DECIMAL(5,4),
    latency_ms     INTEGER      NOT NULL,
    output_summary JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prediction_log_user_created ON prediction_log(user_id, created_at DESC);
CREATE INDEX idx_prediction_log_capability_created ON prediction_log(capability, created_at DESC);
CREATE INDEX idx_prediction_log_created ON prediction_log(created_at);

-- User corrections of predicted values: the training and evaluation signal.
CREATE TABLE user_feedback (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_type    VARCHAR(30)  NOT NULL,
    subject_id      UUID         NOT NULL,
    field           VARCHAR(50)  NOT NULL,
    predicted_value VARCHAR(500),
    corrected_value VARCHAR(500),
    source          VARCHAR(30)  NOT NULL,
    model_version   VARCHAR(64),
    confidence      DECIMAL(5,4),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_feedback_user_field ON user_feedback(user_id, subject_type, field, created_at DESC);
CREATE INDEX idx_user_feedback_subject ON user_feedback(subject_type, subject_id);
