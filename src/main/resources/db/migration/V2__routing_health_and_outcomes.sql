-- AegisAI 6.7: durable routing health, feedback and optimization state.

CREATE TABLE ROUTING_OUTCOME (
    id UUID NOT NULL,
    request_id UUID NOT NULL,
    tenant_id UUID,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(255) NOT NULL,
    routing_strategy VARCHAR(64),
    selected_score DOUBLE PRECISION,
    selected_rank INTEGER,
    candidate_count INTEGER,
    selection_reason VARCHAR(128),
    routing_priority VARCHAR(64),
    extensive_research BOOLEAN NOT NULL DEFAULT FALSE,
    execution_role VARCHAR(255),
    success BOOLEAN NOT NULL,
    failure_category VARCHAR(128),
    latency_ms BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_routing_outcome PRIMARY KEY (id),
    CONSTRAINT uk_routing_outcome_request UNIQUE (request_id)
);

CREATE TABLE ROUTING_HEALTH_PROFILE (
    id UUID NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(255) NOT NULL,
    health_status VARCHAR(32) NOT NULL,
    success_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    consecutive_failures BIGINT NOT NULL DEFAULT 0,
    ewma_latency_ms DOUBLE PRECISION,
    p95_latency_ms DOUBLE PRECISION,
    availability DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    last_success_at TIMESTAMP(6),
    last_failure_at TIMESTAMP(6),
    last_observed_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_routing_health_profile PRIMARY KEY (id),
    CONSTRAINT uk_routing_health_provider_model UNIQUE (provider, model)
);

CREATE INDEX idx_routing_outcome_provider_model
    ON ROUTING_OUTCOME (provider, model);

CREATE INDEX idx_routing_outcome_created_at
    ON ROUTING_OUTCOME (created_at);

CREATE INDEX idx_routing_outcome_tenant
    ON ROUTING_OUTCOME (tenant_id);

CREATE INDEX idx_routing_health_status
    ON ROUTING_HEALTH_PROFILE (health_status);
