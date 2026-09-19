package com.ai.gateway.rag.augmentation;

import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.authentication.AuthenticationContext;

import java.util.UUID;

public interface RagAugmentationService {

    RagAugmentationResult augment(
            UUID tenantId,
            String query,
            RagRequest request);

    /**
     * Authentication-aware RAG augmentation. Personal requests are account-scoped
     * and must not depend on a tenant context; Business requests continue to use
     * tenant-scoped retrieval.
     */
    default RagAugmentationResult augment(
            AuthenticationContext authentication,
            String query,
            RagRequest request) {
        return augment(
                authentication == null ? null : authentication.getTenantId(),
                query,
                request);
    }
}
