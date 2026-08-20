-- AegisAI RAG foundation
-- Executed once for every tenant_<uuid> schema.

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
    CONSTRAINT pk_knowledge_base PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_base_name UNIQUE (name)
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
    CONSTRAINT pk_rag_document PRIMARY KEY (id),
    CONSTRAINT fk_rag_document_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES KNOWLEDGE_BASE(id)
);

CREATE INDEX idx_rag_document_kb
    ON RAG_DOCUMENT (knowledge_base_id);

CREATE INDEX idx_rag_document_status
    ON RAG_DOCUMENT (status);

CREATE TABLE RAG_DOCUMENT_CHUNK (
    id UUID NOT NULL,
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata_json TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_rag_document_chunk PRIMARY KEY (id),
    CONSTRAINT fk_rag_chunk_document
        FOREIGN KEY (document_id) REFERENCES RAG_DOCUMENT(id),
    CONSTRAINT uk_rag_chunk_document_index
        UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_rag_chunk_document
    ON RAG_DOCUMENT_CHUNK (document_id);

CREATE INDEX idx_rag_chunk_created_at
    ON RAG_DOCUMENT_CHUNK (created_at);
