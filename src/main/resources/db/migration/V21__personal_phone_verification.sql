ALTER TABLE PERSONAL_USERS ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE PERSONAL_USERS ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uk_personal_users_phone ON PERSONAL_USERS (phone_number) WHERE phone_number IS NOT NULL;

CREATE TABLE PERSONAL_PHONE_VERIFICATION_CODES (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_personal_phone_verification_codes PRIMARY KEY (id),
    CONSTRAINT fk_personal_phone_verification_user FOREIGN KEY (user_id)
        REFERENCES PERSONAL_USERS (id) ON DELETE CASCADE
);
CREATE INDEX idx_personal_phone_verification_user ON PERSONAL_PHONE_VERIFICATION_CODES(user_id);
CREATE INDEX idx_personal_phone_verification_expires ON PERSONAL_PHONE_VERIFICATION_CODES(expires_at);