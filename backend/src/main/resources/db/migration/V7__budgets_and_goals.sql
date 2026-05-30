-- Phase 8: Budget Planning & Goals
-- ----------------------------------------------------------------------------
-- Budgets: monthly spending limit per category (NULL = overall),
-- one overall and one per-category budget allowed per user.
CREATE TABLE budgets (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category      VARCHAR(100),               -- NULL = overall budget
    monthly_limit DECIMAL(15, 2) NOT NULL,
    rollover      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_budgets_user_id ON budgets(user_id);

-- One budget per (user, category) for non-overall budgets
CREATE UNIQUE INDEX idx_budgets_user_category
    ON budgets(user_id, category) WHERE category IS NOT NULL;

-- Only one overall budget per user
CREATE UNIQUE INDEX idx_budgets_user_overall
    ON budgets(user_id) WHERE category IS NULL;

CREATE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON budgets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ----------------------------------------------------------------------------
-- Financial goals: savings targets with progress tracking.
CREATE TABLE financial_goals (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    target_amount  DECIMAL(15, 2) NOT NULL,
    current_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    target_date    DATE         NOT NULL,
    icon           VARCHAR(50),               -- emoji or lucide name
    status         VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED | ABANDONED
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_goals_user_id ON financial_goals(user_id);

CREATE TRIGGER trg_goals_updated_at
    BEFORE UPDATE ON financial_goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
