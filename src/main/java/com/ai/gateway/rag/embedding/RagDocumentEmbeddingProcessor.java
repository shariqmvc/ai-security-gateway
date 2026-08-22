package com.ai.gateway.rag.embedding;

import com.ai.gateway.rag.document.RagEmbeddingPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagDocumentEmbeddingProcessor {
    private final RagEmbeddingPreparationService preparationService;
    private final RagEmbeddingPersistenceService persistenceService;
    private final EmbeddingProviderFactory providerFactory;
    private final RagEmbeddingProperties properties;

    @Async("gatewayAsyncExecutor")
    public void processAsync(UUID tenantId, UUID documentId) {
        try {
            process(tenantId, documentId);
        } catch (Exception ex) {
            try {
                persistenceService.markFailed(tenantId, documentId,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            } catch (Exception stateException) {
                log.error("Unable to persist embedding failure: tenantId={} documentId={}",
                        tenantId, documentId, stateException);
            }
            log.error("RAG embedding failed: tenantId={} documentId={}", tenantId, documentId, ex);
        }
    }

    public void process(UUID tenantId, UUID documentId) {
        if (!properties.isEnabled()) {
            log.info("RAG embedding disabled; document remains READY_FOR_EMBEDDING: documentId={}", documentId);
            return;
        }

        RagEmbeddingPreparationService.EmbeddingWork work =
                preparationService.load(tenantId, documentId);

        String vectorStore = work.vectorStore();
        if (vectorStore == null || !"PGVECTOR".equalsIgnoreCase(vectorStore.trim())) {
            throw new EmbeddingProviderException(
                    "RAG embedding requires vectorStore=PGVECTOR; configured value: " + vectorStore);
        }

        String providerName = normalizeProvider(work.provider());
        EmbeddingProvider provider = providerFactory.get(providerName);
        String model = work.model();
        if (model == null || model.isBlank()) {
            model = provider.defaultModel();
        }

        persistenceService.markEmbedding(tenantId, documentId);
        List<EmbeddingVector> allVectors = new ArrayList<>(work.texts().size());
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int start = 0; start < work.texts().size(); start += batchSize) {
            int end = Math.min(work.texts().size(), start + batchSize);
            List<String> texts = work.texts().subList(start, end);
            List<EmbeddingVector> vectors = provider.embed(texts, model);
            if (vectors.size() != texts.size()) {
                throw new EmbeddingProviderException("Embedding provider returned " + vectors.size()
                        + " vectors for " + texts.size() + " chunks.");
            }
            allVectors.addAll(vectors);
        }

        persistenceService.persistEmbeddings(tenantId, documentId, providerName, model, allVectors);
        log.info("RAG embedding completed: tenantId={} documentId={} provider={} model={} chunks={} dimension={}",
                tenantId, documentId, providerName, model, allVectors.size(), allVectors.getFirst().dimension());
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank()
                ? properties.getDefaultProvider()
                : provider.trim().toUpperCase();
    }
}
