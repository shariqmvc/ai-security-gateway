-- AIRouter Personal: authoritative account-scoped inference history and telemetry.
-- PostgreSQL is the source of truth for Personal inference data in v1.
-- Content stored here must be masked/sanitized by the application before persistence.

CREATE TABLE PERSONAL_INFERENCE_RUNS (
    id UUID NOT NULL,
    request_id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    workspace_id UUID,
    parent_inference_id UUID,
    api_key_id UUID,
    operation VARCHAR(32) NOT NULL,
    endpoint VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    requested_model VARCHAR(255),
    selected_model VARCHAR(255),
    selected_provider VARCHAR(32),
    billing_mode VARCHAR(32),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    error_code VARCHAR(128),
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_runs PRIMARY KEY (id),
    CONSTRAINT uk_personal_inference_runs_request UNIQUE (request_id),
    CONSTRAINT fk_personal_inference_runs_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_personal_inference_runs_parent
        FOREIGN KEY (parent_inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_personal_inference_runs_api_key
        FOREIGN KEY (api_key_id)
        REFERENCES PERSONAL_API_KEYS (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_personal_inference_runs_account_created
    ON PERSONAL_INFERENCE_RUNS (personal_account_id, created_at DESC);

CREATE INDEX idx_personal_inference_runs_account_status
    ON PERSONAL_INFERENCE_RUNS (personal_account_id, status, created_at DESC);

CREATE INDEX idx_personal_inference_runs_workspace
    ON PERSONAL_INFERENCE_RUNS (personal_account_id, workspace_id, created_at DESC);

CREATE INDEX idx_personal_inference_runs_api_key
    ON PERSONAL_INFERENCE_RUNS (api_key_id, created_at DESC);

CREATE TABLE PERSONAL_INFERENCE_MESSAGES (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    content_json JSONB,
    token_count INTEGER,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_messages PRIMARY KEY (id),
    CONSTRAINT fk_personal_inference_messages_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_inference_messages_run_sequence
    ON PERSONAL_INFERENCE_MESSAGES (inference_id, sequence_no);

CREATE TABLE PERSONAL_PROVIDER_ATTEMPTS (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    attempt_no INTEGER NOT NULL,
    provider VARCHAR(32),
    model VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    request_started_at TIMESTAMP,
    response_received_at TIMESTAMP,
    duration_ms BIGINT,
    time_to_first_token_ms BIGINT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    provider_cost NUMERIC(19,8),
    error_code VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_provider_attempts PRIMARY KEY (id),
    CONSTRAINT fk_personal_provider_attempts_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_personal_provider_attempt
        UNIQUE (inference_id, attempt_no)
);

CREATE INDEX idx_personal_provider_attempts_run
    ON PERSONAL_PROVIDER_ATTEMPTS (inference_id, attempt_no);

CREATE TABLE PERSONAL_INFERENCE_EVENTS (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_stage VARCHAR(64),
    event_data JSONB,
    occurred_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_events PRIMARY KEY (id),
    CONSTRAINT fk_personal_inference_events_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_inference_events_run_time
    ON PERSONAL_INFERENCE_EVENTS (inference_id, occurred_at);

CREATE INDEX idx_personal_inference_events_type
    ON PERSONAL_INFERENCE_EVENTS (event_type, occurred_at);

CREATE TABLE PERSONAL_INFERENCE_USAGE (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    original_input_tokens INTEGER,
    optimized_input_tokens INTEGER,
    tokens_saved INTEGER,
    optimization_ratio NUMERIC(10,6),
    estimated_cost NUMERIC(19,8),
    actual_cost NUMERIC(19,8),
    currency VARCHAR(8),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_usage PRIMARY KEY (id),
    CONSTRAINT uk_personal_inference_usage_run UNIQUE (inference_id),
    CONSTRAINT fk_personal_inference_usage_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE
);

CREATE TABLE PERSONAL_INFERENCE_SECURITY_EVENTS (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    check_type VARCHAR(64) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    risk_score INTEGER,
    categories JSONB,
    detected_items JSONB,
    sanitized_content TEXT,
    details JSONB,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_security_events PRIMARY KEY (id),
    CONSTRAINT fk_personal_inference_security_events_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_inference_security_run
    ON PERSONAL_INFERENCE_SECURITY_EVENTS (inference_id, created_at);

CREATE TABLE PERSONAL_INFERENCE_RETRIEVALS (
    id UUID NOT NULL,
    inference_id UUID NOT NULL,
    knowledge_base_id UUID,
    document_id UUID,
    chunk_id VARCHAR(255),
    rank INTEGER,
    similarity_score DOUBLE PRECISION,
    retrieved_content TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT pk_personal_inference_retrievals PRIMARY KEY (id),
    CONSTRAINT fk_personal_inference_retrievals_run
        FOREIGN KEY (inference_id)
        REFERENCES PERSONAL_INFERENCE_RUNS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_inference_retrievals_run_rank
    ON PERSONAL_INFERENCE_RETRIEVALS (inference_id, rank);
