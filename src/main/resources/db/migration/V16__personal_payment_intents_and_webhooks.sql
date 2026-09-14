CREATE TABLE PERSONAL_PAYMENT_INTENTS (
 id UUID NOT NULL PRIMARY KEY,
 personal_account_id UUID NOT NULL,
 package_code VARCHAR(64) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 amount NUMERIC(19,8) NOT NULL,
 credits NUMERIC(19,8) NOT NULL,
 provider VARCHAR(64) NOT NULL,
 provider_payment_id VARCHAR(255),
 status VARCHAR(32) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL UNIQUE,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 completed_at TIMESTAMP(6)
);
CREATE INDEX idx_personal_payment_intent_account ON PERSONAL_PAYMENT_INTENTS(personal_account_id, created_at);
CREATE UNIQUE INDEX uk_personal_payment_provider_payment ON PERSONAL_PAYMENT_INTENTS(provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE TABLE PERSONAL_PAYMENT_WEBHOOK_EVENTS (
 id UUID NOT NULL PRIMARY KEY,
 provider VARCHAR(64) NOT NULL,
 event_id VARCHAR(255) NOT NULL UNIQUE,
 event_type VARCHAR(64) NOT NULL,
 received_at TIMESTAMP(6) NOT NULL,
 processed_at TIMESTAMP(6),
 status VARCHAR(32) NOT NULL,
 failure_reason VARCHAR(1000)
);
