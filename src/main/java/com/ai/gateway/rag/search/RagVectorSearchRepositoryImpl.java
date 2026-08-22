package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagVectorSearchRepositoryImpl implements RagVectorSearchRepository {

    private static final String SEARCH_SQL = """
            WITH ranked_chunks AS (
                SELECT
                    c.id,
                    c.document_id,
                    d.file_name,
                    c.chunk_index,
                    c.content,
                    c.metadata_json,
                    1 - (
                        c.embedding OPERATOR(public.<=>) ?::public.vector
                    ) AS similarity
                FROM RAG_DOCUMENT_CHUNK c
                JOIN RAG_DOCUMENT d
                  ON d.id = c.document_id
                WHERE d.knowledge_base_id = ?
                  AND d.status = 'INDEXED'
                  AND c.embedding IS NOT NULL
                  AND c.embedding_provider = ?
                  AND c.embedding_model = ?
                  AND c.embedding_dimension = ?
            )
            SELECT
                id,
                document_id,
                file_name,
                chunk_index,
                content,
                metadata_json,
                similarity
            FROM ranked_chunks
            WHERE similarity >= ?
            ORDER BY similarity DESC, document_id ASC, chunk_index ASC, id ASC
            LIMIT ?
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

        return jdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> RagSearchResult.builder()
                        .id(rs.getObject("id", UUID.class))
                        .documentId(rs.getObject("document_id", UUID.class))
                        .fileName(rs.getString("file_name"))
                        .chunkIndex(rs.getInt("chunk_index"))
                        .content(rs.getString("content"))
                        .metadataJson(rs.getString("metadata_json"))
                        .similarity(rs.getDouble("similarity"))
                        .build(),
                queryVector,
                knowledgeBaseId,
                embeddingProvider,
                embeddingModel,
                embeddingDimension,
                minScore,
                topK);
    }
}
