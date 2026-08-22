-- AegisAI RAG Phase 3 embedding infrastructure.
-- The pgvector extension is database-wide; the column itself is tenant-scoped.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE RAG_DOCUMENT_CHUNK
    ADD COLUMN embedding VECTOR,
    ADD COLUMN embedding_provider VARCHAR(64),
    ADD COLUMN embedding_model VARCHAR(255),
    ADD COLUMN embedding_dimension INTEGER,
    ADD COLUMN embedded_at TIMESTAMP(6);

CREATE INDEX idx_rag_chunk_embedding_provider
    ON RAG_DOCUMENT_CHUNK (embedding_provider);

CREATE INDEX idx_rag_chunk_embedding_model
    ON RAG_DOCUMENT_CHUNK (embedding_model);
