-- AIRouter Personal RAG: account-owned storage isolated from Business tenant schemas.
-- Business RAG remains in tenant_<uuid> schemas and is untouched.

CREATE TABLE PERSONAL_KNOWLEDGE_BASES (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    embedding_provider VARCHAR(64),
    embedding_model VARCHAR(255),
    vector_store VARCHAR(64) NOT NULL DEFAULT 'PGVECTOR',
    chunking_strategy VARCHAR(64) NOT NULL DEFAULT 'TOKEN_AWARE',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_personal_knowledge_base PRIMARY KEY (id),
    CONSTRAINT fk_personal_knowledge_base_account FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    CONSTRAINT uk_personal_knowledge_base_account_name UNIQUE (personal_account_id, name)
);

CREATE INDEX idx_personal_knowledge_base_account_created
    ON PERSONAL_KNOWLEDGE_BASES(personal_account_id, created_at DESC);

CREATE TABLE PERSONAL_RAG_DOCUMENTS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
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
    CONSTRAINT pk_personal_rag_document PRIMARY KEY (id),
    CONSTRAINT fk_personal_rag_document_account FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_rag_document_kb FOREIGN KEY (knowledge_base_id)
        REFERENCES PERSONAL_KNOWLEDGE_BASES(id) ON DELETE CASCADE
);

CREATE INDEX idx_personal_rag_document_account_kb
    ON PERSONAL_RAG_DOCUMENTS(personal_account_id, knowledge_base_id, created_at DESC);
CREATE UNIQUE INDEX uk_personal_rag_document_checksum
    ON PERSONAL_RAG_DOCUMENTS(personal_account_id, knowledge_base_id, checksum_sha256)
    WHERE checksum_sha256 IS NOT NULL;

CREATE TABLE PERSONAL_RAG_DOCUMENT_CHUNKS (
    id UUID NOT NULL,
    personal_account_id UUID NOT NULL,
    document_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    record_id VARCHAR(255),
    section_id VARCHAR(255),
    chunk_id VARCHAR(255),
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata_json TEXT,
    embedding VECTOR,
    embedding_provider VARCHAR(64),
    embedding_model VARCHAR(255),
    embedding_dimension INTEGER,
    embedded_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_personal_rag_document_chunk PRIMARY KEY (id),
    CONSTRAINT fk_personal_rag_chunk_account FOREIGN KEY (personal_account_id)
        REFERENCES PERSONAL_ACCOUNTS(id) ON DELETE CASCADE,
    CONSTRAINT fk_personal_rag_chunk_document FOREIGN KEY (document_id)
        REFERENCES PERSONAL_RAG_DOCUMENTS(id) ON DELETE CASCADE,
    CONSTRAINT uk_personal_rag_chunk_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_personal_rag_chunk_document
    ON PERSONAL_RAG_DOCUMENT_CHUNKS(document_id);
CREATE INDEX idx_personal_rag_chunk_embedding
    ON PERSONAL_RAG_DOCUMENT_CHUNKS(embedding_provider, embedding_model, embedding_dimension);
