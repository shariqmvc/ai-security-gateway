package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagVectorSearchRepositoryImpl implements RagVectorSearchRepository {

    private static final Set<Integer> HNSW_DIMENSIONS = Set.of(768, 1536);

    /**
     * Exact/portable path for dimensions that do not have a fixed-dimension
     * HNSW expression index. The embedding column intentionally remains an
     * unconstrained pgvector column so different models can coexist.
     */
    private static final String SEARCH_SQL = """
            SELECT
                ranked.id,
                ranked.document_id,
                ranked.file_name,
                ranked.chunk_index,
                ranked.record_id,
                ranked.section_id,
                ranked.chunk_id,
                ranked.content,
                ranked.metadata_json,
                1 - (ranked.distance) AS similarity
            FROM (
                SELECT
                    c.id,
                    c.document_id,
                    d.file_name,
                    c.chunk_index,
                    c.record_id,
                    c.section_id,
                    c.chunk_id,
                    c.content,
                    c.metadata_json,
                    c.embedding OPERATOR(public.<=>) ?::public.vector AS distance
                FROM RAG_DOCUMENT_CHUNK c
                JOIN RAG_DOCUMENT d
                  ON d.id = c.document_id
                WHERE d.knowledge_base_id = ?
                  AND d.status = 'INDEXED'
                  AND c.embedding IS NOT NULL
                  AND c.embedding_provider = ?
                  AND c.embedding_model = ?
                  AND c.embedding_dimension = ?
                ORDER BY c.embedding OPERATOR(public.<=>) ?::public.vector ASC,
                         c.document_id ASC,
                         c.chunk_index ASC,
                         c.id ASC
                LIMIT ?
            ) ranked
            WHERE ranked.distance <= ?
            ORDER BY ranked.distance ASC,
                     ranked.document_id ASC,
                     ranked.chunk_index ASC,
                     ranked.id ASC
            """;

    /**
     * Fixed-dimension path used for dimensions with HNSW expression indexes.
     * Keeping the cast on the indexed expression allows PostgreSQL/pgvector
     * to use the matching HNSW index instead of falling back to an exact scan.
     */
    private static final String SEARCH_SQL_HNSW_TEMPLATE = """
            SELECT
                ranked.id,
                ranked.document_id,
                ranked.file_name,
                ranked.chunk_index,
                ranked.record_id,
                ranked.section_id,
                ranked.chunk_id,
                ranked.content,
                ranked.metadata_json,
                1 - (ranked.distance) AS similarity
            FROM (
                SELECT
                    c.id,
                    c.document_id,
                    d.file_name,
                    c.chunk_index,
                    c.record_id,
                    c.section_id,
                    c.chunk_id,
                    c.content,
                    c.metadata_json,
                    c.embedding::public.vector(%d) OPERATOR(public.<=>) ?::public.vector(%d) AS distance
                FROM RAG_DOCUMENT_CHUNK c
                JOIN RAG_DOCUMENT d
                  ON d.id = c.document_id
                WHERE d.knowledge_base_id = ?
                  AND d.status = 'INDEXED'
                  AND c.embedding IS NOT NULL
                  AND c.embedding_provider = ?
                  AND c.embedding_model = ?
                  AND c.embedding_dimension = ?
                ORDER BY c.embedding::public.vector(%d) OPERATOR(public.<=>) ?::public.vector(%d) ASC,
                         c.document_id ASC,
                         c.chunk_index ASC,
                         c.id ASC
                LIMIT ?
            ) ranked
            WHERE ranked.distance <= ?
            ORDER BY ranked.distance ASC,
                     ranked.document_id ASC,
                     ranked.chunk_index ASC,
                     ranked.id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RagSearchResult> search(
            UUID knowledgeBaseId,
            String queryVector,
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            int topK,
            double minScore) {

        String sql = sqlForDimension(embeddingDimension);

        if (HNSW_DIMENSIONS.contains(embeddingDimension)) {
            return jdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> mapResult(rs),
                    queryVector,
                    knowledgeBaseId,
                    embeddingProvider,
                    embeddingModel,
                    embeddingDimension,
                    queryVector,
                    topK,
                    1.0d - minScore);
        }

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapResult(rs),
                queryVector,
                knowledgeBaseId,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                queryVector,
                topK,
                1.0d - minScore);
    }

    private String sqlForDimension(int dimension) {
        if (!HNSW_DIMENSIONS.contains(dimension)) {
            return SEARCH_SQL;
        }
        return SEARCH_SQL_HNSW_TEMPLATE.formatted(dimension, dimension, dimension, dimension);
    }

    private RagSearchResult mapResult(java.sql.ResultSet rs) throws java.sql.SQLException {
        return RagSearchResult.builder()
                .id(rs.getObject("id", UUID.class))
                .documentId(rs.getObject("document_id", UUID.class))
                .fileName(rs.getString("file_name"))
                .chunkIndex(rs.getInt("chunk_index"))
                .recordId(rs.getString("record_id"))
                .sectionId(rs.getString("section_id"))
                .chunkId(rs.getString("chunk_id"))
                .content(rs.getString("content"))
                .metadataJson(rs.getString("metadata_json"))
                .similarity(rs.getDouble("similarity"))
                .build();
    }
}
