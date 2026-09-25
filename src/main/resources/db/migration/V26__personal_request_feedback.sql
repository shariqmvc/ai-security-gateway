CREATE TABLE PERSONAL_REQUEST_FEEDBACK (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES PERSONAL_REQUEST_HISTORY(request_id) ON DELETE CASCADE,
    personal_account_id UUID NOT NULL,
    rating VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_personal_request_feedback_request UNIQUE (personal_account_id, request_id),
    CONSTRAINT ck_personal_request_feedback_rating CHECK (rating IN ('UP', 'DOWN'))
);

CREATE INDEX idx_personal_feedback_account_created
    ON PERSONAL_REQUEST_FEEDBACK(personal_account_id, created_at);
