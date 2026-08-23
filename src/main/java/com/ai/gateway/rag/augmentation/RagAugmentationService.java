package com.ai.gateway.rag.augmentation;

import com.ai.gateway.rag.api.RagRequest;

import java.util.UUID;

public interface RagAugmentationService {

    RagAugmentationResult augment(
            UUID tenantId,
            String query,
            RagRequest request);
}
