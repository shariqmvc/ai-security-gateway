-- AegisAI RAG Phase 2 ingestion constraints.
-- A checksum is unique within a knowledge base when present, making
-- re-upload/idempotency checks deterministic without preventing the same
-- source document from legitimately existing in different knowledge bases.

CREATE UNIQUE INDEX uk_rag_document_kb_checksum
    ON RAG_DOCUMENT (knowledge_base_id, checksum_sha256)
    WHERE checksum_sha256 IS NOT NULL;
