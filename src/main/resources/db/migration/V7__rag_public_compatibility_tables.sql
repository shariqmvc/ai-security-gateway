-- AegisAI RAG tenant-isolation compatibility structures.
--
-- Hibernate validates schema-neutral JPA entities against the public/default
-- schema during EntityManagerFactory startup. RAG data itself is authoritative
-- only inside tenant_<uuid> schemas, where V2__rag_foundation.sql is applied.
-- These public tables intentionally remain empty and reject all writes.

CREATE TABLE KNOWLEDGE_BASE (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    embedding_provider VARCHAR(64),
    embedding_model VARCHAR(255),
    vector_store VARCHAR(64) NOT NULL DEFAULT 'POSTGRES',
    chunking_strategy VARCHAR(64) NOT NULL DEFAULT 'TOKEN_AWARE',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_public_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uk_public_knowledge_base_name UNIQUE (name)
);

CREATE TABLE RAG_DOCUMENT (
    id UUID NOT NULL,
    knowledge_base_id UUID NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255),
    file_size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    content TEXT,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_public_rag_document PRIMARY KEY (id),
    CONSTRAINT fk_public_rag_document_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES KNOWLEDGE_BASE(id)
);

CREATE INDEX idx_public_rag_document_kb
    ON RAG_DOCUMENT (knowledge_base_id);

CREATE INDEX idx_public_rag_document_status
    ON RAG_DOCUMENT (status);

CREATE TABLE RAG_DOCUMENT_CHUNK (
    id UUID NOT NULL,
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata_json TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_public_rag_document_chunk PRIMARY KEY (id),
    CONSTRAINT fk_public_rag_chunk_document
        FOREIGN KEY (document_id) REFERENCES RAG_DOCUMENT(id),
    CONSTRAINT uk_public_rag_chunk_document_index
        UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_public_rag_chunk_document
    ON RAG_DOCUMENT_CHUNK (document_id);

CREATE INDEX idx_public_rag_chunk_created_at
    ON RAG_DOCUMENT_CHUNK (created_at);

CREATE OR REPLACE FUNCTION reject_public_rag_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'RAG table % is not writable in public schema; establish tenant schema routing first',
        TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_public_knowledge_base_write
    ON KNOWLEDGE_BASE;
CREATE TRIGGER trg_reject_public_knowledge_base_write
    BEFORE INSERT OR UPDATE OR DELETE ON KNOWLEDGE_BASE
    FOR EACH ROW EXECUTE FUNCTION reject_public_rag_write();

DROP TRIGGER IF EXISTS trg_reject_public_rag_document_write
    ON RAG_DOCUMENT;
CREATE TRIGGER trg_reject_public_rag_document_write
    BEFORE INSERT OR UPDATE OR DELETE ON RAG_DOCUMENT
    FOR EACH ROW EXECUTE FUNCTION reject_public_rag_write();

DROP TRIGGER IF EXISTS trg_reject_public_rag_chunk_write
    ON RAG_DOCUMENT_CHUNK;
CREATE TRIGGER trg_reject_public_rag_chunk_write
    BEFORE INSERT OR UPDATE OR DELETE ON RAG_DOCUMENT_CHUNK
    FOR EACH ROW EXECUTE FUNCTION reject_public_rag_write();
