package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagKeywordSearchRepositoryImpl implements RagKeywordSearchRepository {

    private static final String SEARCH_SQL = """
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
                ts_rank_cd(
                    to_tsvector('simple', coalesce(c.content, '')),
                    plainto_tsquery('simple', ?)
                ) AS keyword_score
            FROM RAG_DOCUMENT_CHUNK c
            JOIN RAG_DOCUMENT d ON d.id = c.document_id
            WHERE d.knowledge_base_id = ?
              AND d.status = 'INDEXED'
              AND c.content IS NOT NULL
              AND to_tsvector('simple', coalesce(c.content, ''))
                    @@ plainto_tsquery('simple', ?)
            ORDER BY keyword_score DESC, c.document_id ASC, c.chunk_index ASC, c.id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RagSearchResult> search(UUID knowledgeBaseId, String query, int limit) {
        return jdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> RagSearchResult.builder()
                        .id(rs.getObject("id", UUID.class))
                        .documentId(rs.getObject("document_id", UUID.class))
                        .fileName(rs.getString("file_name"))
                        .chunkIndex(rs.getInt("chunk_index"))
                        .recordId(rs.getString("record_id"))
                        .sectionId(rs.getString("section_id"))
                        .chunkId(rs.getString("chunk_id"))
                        .content(rs.getString("content"))
                        .metadataJson(rs.getString("metadata_json"))
                        .similarity(rs.getDouble("keyword_score"))
                        .build(),
                query,
                knowledgeBaseId,
                query,
                limit);
    }
}
