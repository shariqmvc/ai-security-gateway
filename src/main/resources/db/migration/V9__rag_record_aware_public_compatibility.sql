-- AegisAI RAG Phase 4.2 record-aware metadata for the public compatibility schema.
-- The public RAG tables are retained for Hibernate schema validation/compatibility,
-- while tenant schemas remain the authoritative operational storage.

ALTER TABLE RAG_DOCUMENT_CHUNK
    ADD COLUMN IF NOT EXISTS record_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS section_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS chunk_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_public_rag_chunk_record
    ON RAG_DOCUMENT_CHUNK (record_id)
    WHERE record_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_public_rag_chunk_record_section
    ON RAG_DOCUMENT_CHUNK (record_id, section_id)
    WHERE record_id IS NOT NULL;
