CREATE TABLE PERSONAL_ACCOUNT_POLICIES (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_account_id UUID NOT NULL UNIQUE REFERENCES PERSONAL_ACCOUNTS(id),
    quota_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    requests_per_minute BIGINT NOT NULL DEFAULT 0,
    requests_per_day BIGINT NOT NULL DEFAULT 0,
    monthly_token_quota BIGINT NOT NULL DEFAULT 0,
    max_input_tokens BIGINT NOT NULL DEFAULT 0,
    max_output_tokens BIGINT NOT NULL DEFAULT 0,
    max_concurrent_requests BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE PERSONAL_ACCOUNT_FEATURES (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_account_id UUID NOT NULL REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    feature VARCHAR(64) NOT NULL,
    CONSTRAINT uq_personal_account_feature UNIQUE (personal_account_id, feature)
);

CREATE TABLE PERSONAL_ACCOUNT_FREE_MODELS (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_account_id UUID NOT NULL REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    model_key VARCHAR(160) NOT NULL,
    CONSTRAINT uq_personal_account_free_model UNIQUE (personal_account_id, model_key)
);

CREATE INDEX idx_personal_account_features_account
    ON PERSONAL_ACCOUNT_FEATURES(personal_account_id);

CREATE INDEX idx_personal_account_free_models_account
    ON PERSONAL_ACCOUNT_FREE_MODELS(personal_account_id);
