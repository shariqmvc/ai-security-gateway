CREATE TABLE PERSONAL_CHAT_SESSIONS (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    personal_account_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_account
        FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_session_account_updated
    ON PERSONAL_CHAT_SESSIONS(personal_account_id, updated_at DESC);

CREATE TABLE PERSONAL_CHAT_MESSAGES (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id)
        REFERENCES PERSONAL_CHAT_SESSIONS(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_chat_message_role
        CHECK (role IN ('user','assistant','system')),
    CONSTRAINT uq_chat_message_sequence
        UNIQUE (session_id, sequence_no)
);

CREATE INDEX idx_chat_message_session_sequence
    ON PERSONAL_CHAT_MESSAGES(session_id, sequence_no);
