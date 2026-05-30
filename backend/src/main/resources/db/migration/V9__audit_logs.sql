-- Phase 11: Audit log for security-relevant user actions.
-- Used by the account/audit-log endpoint and for compliance investigations.
CREATE TABLE audit_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    action        VARCHAR(60)  NOT NULL,
    resource_type VARCHAR(40),
    resource_id   VARCHAR(80),
    ip_address    VARCHAR(45),    -- IPv6 max length
    user_agent    VARCHAR(500),
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id    ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_action     ON audit_logs(action);
