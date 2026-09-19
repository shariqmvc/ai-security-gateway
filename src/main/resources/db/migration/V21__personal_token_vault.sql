-- AIRouter Personal: account-scoped encrypted PII token vault.
-- Personal requests must never depend on Business tenant schema routing.

CREATE TABLE PERSONAL_TOKEN_VAULT (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    request_uuid UUID NOT NULL,
    token VARCHAR(100) NOT NULL,
    encrypted_value TEXT NOT NULL,
    pii_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_token_vault PRIMARY KEY (id),
    CONSTRAINT fk_personal_token_vault_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_personal_token_vault_request_token
        UNIQUE (request_uuid, token)
);

CREATE INDEX idx_personal_token_vault_account_request
    ON PERSONAL_TOKEN_VAULT (personal_account_id, request_uuid);

CREATE INDEX idx_personal_token_vault_request
    ON PERSONAL_TOKEN_VAULT (request_uuid);
