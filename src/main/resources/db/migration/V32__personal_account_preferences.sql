CREATE TABLE PERSONAL_ACCOUNT_PREFERENCES (
    id UUID PRIMARY KEY,
    personal_account_id UUID NOT NULL UNIQUE,
    default_provider VARCHAR(32),
    default_model VARCHAR(160),
    billing_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    routing_priority VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_personal_account_preferences_account
        FOREIGN KEY (personal_account_id) REFERENCES PERSONAL_ACCOUNTS(id)
);

CREATE INDEX idx_personal_account_preferences_account
    ON PERSONAL_ACCOUNT_PREFERENCES(personal_account_id);
