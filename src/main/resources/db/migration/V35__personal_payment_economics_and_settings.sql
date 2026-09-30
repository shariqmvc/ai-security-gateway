ALTER TABLE PERSONAL_PAYMENT_INTENTS
    ADD COLUMN base_amount NUMERIC(19,8) NOT NULL DEFAULT 0,
    ADD COLUMN fee_amount NUMERIC(19,8) NOT NULL DEFAULT 0,
    ADD COLUMN tax_amount NUMERIC(19,8) NOT NULL DEFAULT 0,
    ADD COLUMN checkout_session_id VARCHAR(255),
    ADD COLUMN receipt_url VARCHAR(1000),
    ADD COLUMN invoice_url VARCHAR(1000);

CREATE UNIQUE INDEX uk_personal_payment_checkout_session
    ON PERSONAL_PAYMENT_INTENTS(checkout_session_id)
    WHERE checkout_session_id IS NOT NULL;

CREATE TABLE PERSONAL_BILLING_SETTINGS (
    id UUID PRIMARY KEY,
    personal_account_id UUID NOT NULL UNIQUE,
    low_balance_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    low_balance_threshold NUMERIC(19,8) NOT NULL DEFAULT 2,
    auto_top_up_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_top_up_amount NUMERIC(19,8) NOT NULL DEFAULT 10,
    monthly_spend_cap NUMERIC(19,8),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_personal_billing_settings_account
        FOREIGN KEY (personal_account_id) REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    CONSTRAINT ck_personal_billing_settings_threshold CHECK (low_balance_threshold >= 0),
    CONSTRAINT ck_personal_billing_settings_auto_amount CHECK (auto_top_up_amount > 0),
    CONSTRAINT ck_personal_billing_settings_spend_cap CHECK (monthly_spend_cap IS NULL OR monthly_spend_cap > 0)
);

CREATE INDEX idx_personal_billing_settings_account
    ON PERSONAL_BILLING_SETTINGS(personal_account_id);
