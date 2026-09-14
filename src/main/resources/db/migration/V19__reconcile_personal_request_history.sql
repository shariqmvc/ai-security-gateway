-- AIRouter Personal: reconcile the Personal request-history schema.
--
-- V18 was previously applied against an older physical version of this
-- table. The table is currently empty, so it can be safely recreated.
--
-- Personal request history remains account-scoped and contains no tenant_id.

DROP TABLE IF EXISTS PERSONAL_REQUEST_HISTORY;

CREATE TABLE PERSONAL_REQUEST_HISTORY (
    id UUID NOT NULL,
    request_id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(32),
    model VARCHAR(255),
    billing_mode VARCHAR(32),
    routing_strategy VARCHAR(64),
    masked_prompt TEXT,
    masked_response TEXT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    estimated_input_tokens INTEGER,
    estimated_optimized_tokens INTEGER,
    estimated_tokens_saved INTEGER,
    context_window_tokens INTEGER,
    latency_ms BIGINT,
    provider_latency_ms BIGINT,
    cost NUMERIC(19,8),
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    rag_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    error_category VARCHAR(128),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_request_history
        PRIMARY KEY (id),

    CONSTRAINT uk_personal_request_history_request
        UNIQUE (request_id),

    CONSTRAINT fk_personal_request_history_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_request_account_created
    ON PERSONAL_REQUEST_HISTORY (personal_account_id, created_at);

CREATE INDEX idx_personal_request_account_status
    ON PERSONAL_REQUEST_HISTORY (personal_account_id, status);