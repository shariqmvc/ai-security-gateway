package com.ai.gateway.rag.document;

import com.ai.gateway.rag.ingestion.DocumentChunk;
import com.ai.gateway.rag.ingestion.DocumentChunker;
import com.ai.gateway.rag.ingestion.DocumentChunkerFactory;
import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import com.ai.gateway.rag.ingestion.DocumentParser;
import com.ai.gateway.rag.ingestion.ParsedDocument;
import com.ai.gateway.rag.ingestion.TextNormalizer;
import com.ai.gateway.rag.embedding.RagDocumentEmbeddingProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagDocumentIngestionProcessor {

    private final RagDocumentIngestionPersistenceService persistenceService;
    private final DocumentParser documentParser;
    private final TextNormalizer textNormalizer;
    private final DocumentChunkerFactory documentChunkerFactory;
    private final RagDocumentEmbeddingProcessor embeddingProcessor;

    @Async("gatewayAsyncExecutor")
    public void processAsync(UUID tenantId,
                             UUID documentId,
                             Path temporaryFile,
                             String fileName,
                             String contentType) {
        try {
            persistenceService.markProcessing(tenantId, documentId);

            ParsedDocument parsed = documentParser.parse(
                    temporaryFile,
                    fileName,
                    contentType);
            String normalizedText = textNormalizer.normalize(parsed.text());

            ChunkingStrategy chunkingStrategy =
                    persistenceService.loadChunkingStrategy(tenantId, documentId);
            DocumentChunker documentChunker =
                    documentChunkerFactory.resolve(chunkingStrategy);

            List<DocumentChunk> chunks = documentChunker.chunk(normalizedText);

            persistenceService.complete(
                    tenantId,
                    documentId,
                    normalizedText,
                    parsed.detectedContentType(),
                    chunks);

            embeddingProcessor.processAsync(tenantId, documentId);

            log.info(
                    "RAG document ingestion completed: tenantId={} documentId={} chunks={}",
                    tenantId,
                    documentId,
                    chunks.size());
        } catch (Exception ex) {
            try {
                persistenceService.markFailed(
                        tenantId,
                        documentId,
                        ex.getMessage() == null
                                ? ex.getClass().getSimpleName()
                                : ex.getMessage());
            } catch (Exception stateException) {
                log.error(
                        "Unable to persist RAG ingestion failure: tenantId={} documentId={}",
                        tenantId,
                        documentId,
                        stateException);
            }

            log.error(
                    "RAG document ingestion failed: tenantId={} documentId={}",
                    tenantId,
                    documentId,
                    ex);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (Exception cleanupException) {
                log.warn(
                        "Unable to remove RAG ingestion temporary file: {}",
                        temporaryFile,
                        cleanupException);
            }
        }
    }
}
