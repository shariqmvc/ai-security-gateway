-- AIRoute Personal v1 P2: user-owned provider connections.
-- Provider credentials are encrypted application secrets and are deliberately
-- independent from enterprise TENANT provider configuration.

CREATE TABLE PERSONAL_PROVIDER_CONNECTIONS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    encrypted_api_key VARCHAR(4096) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_validated_at TIMESTAMP(6),
    validation_message VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_personal_provider_connections PRIMARY KEY (id),
    CONSTRAINT uk_personal_provider_account_provider
        UNIQUE (personal_account_id, provider),
    CONSTRAINT fk_personal_provider_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_provider_connections_account
    ON PERSONAL_PROVIDER_CONNECTIONS (personal_account_id);

CREATE INDEX idx_personal_provider_connections_status
    ON PERSONAL_PROVIDER_CONNECTIONS (status);
