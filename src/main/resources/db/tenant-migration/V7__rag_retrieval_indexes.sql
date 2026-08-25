-- AegisAI RAG Phase 4 retrieval-performance indexes.
-- Keep these indexes tenant-scoped; every tenant schema receives its own index.
--
-- RAG_DOCUMENT_CHUNK.embedding intentionally uses pgvector's unconstrained
-- `vector` type because different embedding providers/models can have different
-- dimensions. PostgreSQL/pgvector requires a fixed dimension for an HNSW index,
-- so a direct index on `embedding` fails with:
--   ERROR: column does not have dimensions
--
-- Use fixed-dimension expression indexes for the dimensions supported by the
-- gateway's default/common embedding models. The partial predicates prevent
-- casts of rows belonging to other dimensions. Additional dimensions can be
-- added as a new migration when a model using that dimension is introduced.

CREATE INDEX idx_rag_chunk_embedding_hnsw_768
    ON RAG_DOCUMENT_CHUNK
    USING hnsw ((embedding::vector(768)) vector_cosine_ops)
    WHERE embedding IS NOT NULL
      AND embedding_dimension = 768;

CREATE INDEX idx_rag_chunk_embedding_hnsw_1536
    ON RAG_DOCUMENT_CHUNK
    USING hnsw ((embedding::vector(1536)) vector_cosine_ops)
    WHERE embedding IS NOT NULL
      AND embedding_dimension = 1536;

-- The functional GIN index avoids rebuilding the same tsvector expression
-- for every candidate row during keyword retrieval.
CREATE INDEX idx_rag_chunk_content_fts
    ON RAG_DOCUMENT_CHUNK
    USING gin (to_tsvector('simple', coalesce(content, '')));
