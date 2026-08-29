-- AIRoute Personal v1 P10.2: prepaid credit wallet, immutable ledger and reservations.
-- Payment-provider integration remains P12. These tables establish the accounting
-- boundary and do not perform checkout or payment processing.

CREATE TABLE PERSONAL_CREDIT_WALLETS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    balance NUMERIC(19,8) NOT NULL DEFAULT 0,
    reserved_balance NUMERIC(19,8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT,
    CONSTRAINT pk_personal_credit_wallets PRIMARY KEY (id),
    CONSTRAINT uk_personal_credit_wallet_account UNIQUE (personal_account_id),
    CONSTRAINT fk_personal_credit_wallet_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_personal_credit_wallet_balance CHECK (balance >= 0),
    CONSTRAINT ck_personal_credit_wallet_reserved CHECK (reserved_balance >= 0),
    CONSTRAINT ck_personal_credit_wallet_available CHECK (reserved_balance <= balance)
);

CREATE TABLE PERSONAL_CREDIT_LEDGER (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,8) NOT NULL,
    reference_id VARCHAR(128),
    reservation_id UUID,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_personal_credit_ledger PRIMARY KEY (id),
    CONSTRAINT uk_personal_credit_ledger_reference UNIQUE (reference_id),
    CONSTRAINT fk_personal_credit_ledger_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE
);

CREATE TABLE PERSONAL_CREDIT_RESERVATIONS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    reserved_amount NUMERIC(19,8) NOT NULL,
    captured_amount NUMERIC(19,8) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_personal_credit_reservations PRIMARY KEY (id),
    CONSTRAINT uk_personal_credit_reservation_reference UNIQUE (reference_id),
    CONSTRAINT fk_personal_credit_reservation_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_personal_credit_reservation_amount CHECK (reserved_amount > 0),
    CONSTRAINT ck_personal_credit_reservation_captured CHECK (captured_amount >= 0),
    CONSTRAINT ck_personal_credit_reservation_capture_limit CHECK (captured_amount <= reserved_amount)
);

CREATE INDEX idx_personal_credit_ledger_account_created
    ON PERSONAL_CREDIT_LEDGER (personal_account_id, created_at DESC);

CREATE INDEX idx_personal_credit_reservations_account_status
    ON PERSONAL_CREDIT_RESERVATIONS (personal_account_id, status);
