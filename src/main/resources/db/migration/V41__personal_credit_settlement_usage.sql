-- Persist provider usage before cost estimation so successful inference can be settled after transient failures.
ALTER TABLE PERSONAL_CREDIT_RESERVATIONS
    ADD COLUMN settlement_provider VARCHAR(32),
    ADD COLUMN settlement_model VARCHAR(255),
    ADD COLUMN settlement_input_tokens INTEGER,
    ADD COLUMN settlement_output_tokens INTEGER;

ALTER TABLE PERSONAL_CREDIT_RESERVATIONS
    ADD CONSTRAINT ck_personal_credit_reservation_settlement_tokens
        CHECK ((settlement_input_tokens IS NULL OR settlement_input_tokens >= 0)
           AND (settlement_output_tokens IS NULL OR settlement_output_tokens >= 0));
