-- Durable Personal credit settlement and safe reservation recovery.
ALTER TABLE PERSONAL_CREDIT_RESERVATIONS
    ADD COLUMN settlement_amount NUMERIC(19,8),
    ADD COLUMN provider_invocation_started BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE PERSONAL_CREDIT_RESERVATIONS
    ADD CONSTRAINT ck_personal_credit_reservation_settlement_amount
        CHECK (settlement_amount IS NULL OR
               (settlement_amount >= 0 AND settlement_amount <= reserved_amount));

CREATE INDEX idx_personal_credit_reservations_recovery
    ON PERSONAL_CREDIT_RESERVATIONS (status, provider_invocation_started, created_at);
