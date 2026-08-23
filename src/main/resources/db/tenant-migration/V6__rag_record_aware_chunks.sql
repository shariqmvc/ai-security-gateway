-- AegisAI RAG Phase 4.2 record-aware retrieval metadata.
-- Existing token-aware chunks remain valid; structured identity columns are
-- nullable so this migration is backward compatible.

ALTER TABLE RAG_DOCUMENT_CHUNK
    ADD COLUMN IF NOT EXISTS record_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS section_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS chunk_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_record
    ON RAG_DOCUMENT_CHUNK (record_id)
    WHERE record_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rag_chunk_record_section
    ON RAG_DOCUMENT_CHUNK (record_id, section_id)
    WHERE record_id IS NOT NULL;
