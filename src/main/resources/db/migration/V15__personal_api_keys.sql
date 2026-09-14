-- AIRouter Personal v1 P7: dedicated developer API keys.
-- Raw keys are never persisted. Only SHA-256 key hashes are stored.

CREATE TABLE PERSONAL_API_KEYS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(24) NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    CONSTRAINT pk_personal_api_keys PRIMARY KEY (id),
    CONSTRAINT uk_personal_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_personal_api_keys_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_api_keys_account
    ON PERSONAL_API_KEYS (personal_account_id, created_at DESC);
CREATE INDEX idx_personal_api_keys_prefix
    ON PERSONAL_API_KEYS (key_prefix);
