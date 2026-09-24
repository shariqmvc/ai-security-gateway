CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS personal_security_semantic_examples (
    id BIGSERIAL PRIMARY KEY,
    corpus_id VARCHAR(64) NOT NULL UNIQUE,
    text TEXT NOT NULL,
    primary_label VARCHAR(64) NOT NULL,
    labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    family VARCHAR(128) NOT NULL,
    variant VARCHAR(64) NOT NULL,
    language VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    embedding vector(768) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_psse_primary_label
    ON personal_security_semantic_examples(primary_label);

CREATE INDEX IF NOT EXISTS idx_psse_family
    ON personal_security_semantic_examples(family);

CREATE INDEX IF NOT EXISTS idx_psse_language
    ON personal_security_semantic_examples(language);

CREATE INDEX IF NOT EXISTS idx_psse_embedding_hnsw
    ON personal_security_semantic_examples
    USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE personal_security_semantic_examples IS
'Offline semantic-security corpus for AIRouter P11.9 semantic firewall evaluation.';

COMMENT ON COLUMN personal_security_semantic_examples.embedding IS
'768-dimensional nomic-embed-text embedding.';
