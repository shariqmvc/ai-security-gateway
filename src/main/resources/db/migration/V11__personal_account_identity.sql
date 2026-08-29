-- AIRoute Personal v1: account and identity foundation.
-- Personal users are deliberately independent from enterprise TENANTS.
-- Passwords and session/verification secrets are never stored in plaintext.

CREATE TABLE PERSONAL_USERS (
    id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_login_at TIMESTAMP(6),
    CONSTRAINT pk_personal_users PRIMARY KEY (id),
    CONSTRAINT uk_personal_users_email UNIQUE (email)
);

CREATE TABLE PERSONAL_ACCOUNTS (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    plan VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_personal_accounts PRIMARY KEY (id),
    CONSTRAINT uk_personal_accounts_user UNIQUE (user_id),
    CONSTRAINT fk_personal_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES PERSONAL_USERS (id)
        ON DELETE CASCADE
);

CREATE TABLE PERSONAL_SESSIONS (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    CONSTRAINT pk_personal_sessions PRIMARY KEY (id),
    CONSTRAINT uk_personal_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_personal_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES PERSONAL_USERS (id)
        ON DELETE CASCADE
);

CREATE TABLE PERSONAL_EMAIL_VERIFICATION_TOKENS (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_personal_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_personal_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_personal_verification_user
        FOREIGN KEY (user_id)
        REFERENCES PERSONAL_USERS (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_personal_users_email
    ON PERSONAL_USERS (email);

CREATE INDEX idx_personal_sessions_user_id
    ON PERSONAL_SESSIONS (user_id);

CREATE INDEX idx_personal_sessions_expires_at
    ON PERSONAL_SESSIONS (expires_at);

CREATE INDEX idx_personal_verification_user_id
    ON PERSONAL_EMAIL_VERIFICATION_TOKENS (user_id);
