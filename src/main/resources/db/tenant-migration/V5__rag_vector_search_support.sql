-- AegisAI RAG Phase 3 vector-search support.
-- Vector similarity is executed with pgvector's cosine-distance operator.
-- The tenant table remains authoritative; no public operational data is used.

CREATE INDEX idx_rag_chunk_embedding_lookup
    ON RAG_DOCUMENT_CHUNK (embedding_provider, embedding_model, embedding_dimension)
    WHERE embedding IS NOT NULL;
