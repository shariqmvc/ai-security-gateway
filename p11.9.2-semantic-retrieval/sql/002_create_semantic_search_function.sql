CREATE OR REPLACE FUNCTION search_personal_security_semantic_examples(
    query_embedding vector(768),
    requested_top_k integer DEFAULT 5
)
RETURNS TABLE (
    corpus_id VARCHAR,
    text TEXT,
    primary_label VARCHAR,
    labels JSONB,
    family VARCHAR,
    variant VARCHAR,
    language VARCHAR,
    severity VARCHAR,
    similarity DOUBLE PRECISION
)
LANGUAGE SQL
AS $$
    SELECT
        e.corpus_id,
        e.text,
        e.primary_label,
        e.labels,
        e.family,
        e.variant,
        e.language,
        e.severity,
        1 - (e.embedding <=> query_embedding) AS similarity
    FROM personal_security_semantic_examples e
    ORDER BY e.embedding <=> query_embedding
    LIMIT GREATEST(requested_top_k, 1);
$$;
