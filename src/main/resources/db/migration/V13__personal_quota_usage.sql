-- AIRoute Personal v1 P10: account-scoped quota usage foundation.
-- Personal quota state is deliberately independent from enterprise tenant
-- quota/entitlement state. Enforcement is introduced in later P10 steps.

CREATE TABLE PERSONAL_QUOTA_USAGE (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    period_type VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    token_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT,
    CONSTRAINT pk_personal_quota_usage PRIMARY KEY (id),
    CONSTRAINT uk_personal_quota_period
        UNIQUE (personal_account_id, period_type, period_start),
    CONSTRAINT fk_personal_quota_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_quota_account_period
    ON PERSONAL_QUOTA_USAGE (personal_account_id, period_type, period_start);
