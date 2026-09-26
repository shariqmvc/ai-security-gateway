ALTER TABLE PERSONAL_KNOWLEDGE_BASES
    ADD COLUMN IF NOT EXISTS provider_scope VARCHAR(32) NOT NULL DEFAULT 'GLOBAL';

UPDATE PERSONAL_KNOWLEDGE_BASES
SET provider_scope = 'GLOBAL'
WHERE provider_scope IS NULL;

CREATE INDEX IF NOT EXISTS idx_personal_knowledge_base_account_scope
    ON PERSONAL_KNOWLEDGE_BASES(personal_account_id, provider_scope, created_at DESC);
